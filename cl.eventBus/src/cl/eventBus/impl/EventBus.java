package cl.eventBus.impl;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import cl.eventBus.api.EventDescriptor;
import cl.eventBus.api.EventException;
import cl.eventBus.api.IEventBus;
import cl.eventBus.api.RequestEventDescriptor;

/**
 * The class <b>EventBus</b> allows to simplify the use of E4 IEventBroker.<br>
 */
@Component
public class EventBus implements IEventBus
{
  @Reference
  private IEventBroker eventBroker;

  private final Map<Map.Entry<EventDescriptor<?>, Consumer<?>>, EventHandler> eventDescriptorMap = new ConcurrentHashMap<>();
  private final Map<Map.Entry<RequestEventDescriptor<?, ?>, Function<?, ?>>, EventHandler> requestEventDescriptorMap = new ConcurrentHashMap<>();
  private final Set<ReceiveResponsesConsumer<?>> responseConsumerSet = ConcurrentHashMap.newKeySet();

  private boolean DEBUG = false;
  private final static String COUNT_DOWN_LATCH_PROPERTY = "countDownLatch";
  private final static String THROWABLE_LIST_PROPERTY = "throwableList";

  private static int THREAD_NUMBER = 0;
  private final ThreadFactory threadFactory = runnable -> {
    Thread thread = new Thread(runnable, "EventBus Thread #" + THREAD_NUMBER++);
    thread.setDaemon(true);
    return thread;
  };

  private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(threadFactory);
  private final ExecutorService executorService = Executors.newCachedThreadPool(threadFactory);

  /**
   * Subscribe
   *
   * @param eventDescriptor
   * @param consumer
   */
  @Override
  public <E> boolean subscribe(EventDescriptor<E> eventDescriptor, Consumer<? super E> consumer)
  {
    Objects.requireNonNull(eventDescriptor, "eventDescriptor is null");
    Objects.requireNonNull(consumer, "consumer is null");

    // check
    checkSameEventTypeClass(eventDescriptor.getTopic(), eventDescriptor.getEventDescriptorClass());

    if (DEBUG)
      printDebug("SUBSCRIBE", eventDescriptor);

    Map.Entry<EventDescriptor<?>, Consumer<?>> entry = new AbstractMap.SimpleEntry<>(eventDescriptor, consumer);
    EventHandler eventHandler = eventDescriptorMap.get(entry);
    if (eventHandler == null)
    {
      eventHandler = event -> {
        Object eventData = event.getProperty(IEventBroker.DATA);
        if (acceptDataForEvent(eventData, eventDescriptor.getEventDescriptorClass()))
        {
          E data = eventDescriptor.getEventDescriptorClass().cast(eventData);
          //          consumer.accept(data);
          executorService.execute(() -> {
            try
            {
              consumer.accept(data);
            }
            catch(Exception e)
            {
              Optional.ofNullable(event.getProperty(THROWABLE_LIST_PROPERTY))
                .filter(List.class::isInstance)
                .map(List.class::cast)
                .ifPresent(list -> list.add(e));
            }
            finally
            {
              Optional.ofNullable(event.getProperty(COUNT_DOWN_LATCH_PROPERTY))
                .filter(CountDownLatch.class::isInstance)
                .map(CountDownLatch.class::cast)
                .ifPresent(CountDownLatch::countDown);
            }
          });
        }
      };

      if (eventBroker.subscribe(eventDescriptor.getTopic(), null, eventHandler, true))
      {
        eventDescriptorMap.put(entry, eventHandler);
        return true;
      }
    }

    return false;
  }

  /**
   * Subscribe
   *
   * @param requestEventDescriptor
   * @param function
   */
  @Override
  public <E, R> boolean subscribe(RequestEventDescriptor<E, R> requestEventDescriptor, Function<? super E, ? extends R> function)
  {
    Objects.requireNonNull(requestEventDescriptor, "requestEventDescriptor is null");
    Objects.requireNonNull(function, "function is null");

    // check
    checkSameEventTypeClass(requestEventDescriptor.getRequestEventDescriptor().getTopic(), requestEventDescriptor.getRequestEventDescriptor().getEventDescriptorClass());
    checkSameEventTypeClass(requestEventDescriptor.getReplyEventDescriptor().getTopic(), requestEventDescriptor.getReplyEventDescriptor().getEventDescriptorClass());

    if (DEBUG)
      printDebug("SUBSCRIBE", requestEventDescriptor);

    Map.Entry<RequestEventDescriptor<?, ?>, Function<?, ?>> entry = new AbstractMap.SimpleEntry<>(requestEventDescriptor, function);
    EventHandler eventHandler = requestEventDescriptorMap.get(entry);
    if (eventHandler == null)
    {
      eventHandler = event -> {
        Object eventData = event.getProperty(IEventBroker.DATA);
        if (acceptDataForEvent(eventData, requestEventDescriptor.getRequestEventDescriptor().getEventDescriptorClass()))
        {
          @SuppressWarnings("unchecked")
          List<ReceiveResponsesConsumer<R>> responseConsumers = responseConsumerSet.stream()
            .filter(rc -> rc.requestEventDescriptor.equals(requestEventDescriptor))
            .map(rc -> (ReceiveResponsesConsumer<R>) rc)
            .collect(Collectors.toList());

          Runnable runnable = () -> {
            if (!responseConsumers.isEmpty())
            {
              Thread currentThread = Thread.currentThread();
              responseConsumers.forEach(rc -> rc.addThread(currentThread));

              try
              {
                E data = requestEventDescriptor.getRequestEventDescriptor().getEventDescriptorClass().cast(eventData);
                R response = function.apply(data);

                responseConsumers.forEach(rc -> rc.accept(response));
              }
              catch(Throwable th)
              {
                responseConsumers.forEach(rc -> rc.completeExceptionally(th, true));
              }
              finally
              {
                responseConsumers.forEach(rc -> rc.removeThread(currentThread));
              }
            }
          };

          executorService.execute(runnable);
        }
      };

      if (eventBroker.subscribe(requestEventDescriptor.getRequestEventDescriptor().getTopic(), null, eventHandler, true))
      {
        requestEventDescriptorMap.put(entry, eventHandler);
        return true;
      }
    }

    return false;
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Unsubscribe
   *
   * @param eventDescriptor
   * @param consumer
   */
  @Override
  public <E> boolean unsubscribe(EventDescriptor<E> eventDescriptor, Consumer<? super E> consumer)
  {
    Objects.requireNonNull(eventDescriptor, "eventDescriptor is null");
    Objects.requireNonNull(consumer, "consumer is null");

    if (DEBUG)
      printDebug("UNSUBSCRIBE", eventDescriptor);

    Map.Entry<EventDescriptor<?>, Consumer<?>> entry = new AbstractMap.SimpleEntry<>(eventDescriptor, consumer);
    EventHandler eventHandler = eventDescriptorMap.remove(entry);
    if (eventHandler != null)
    {
      if (eventBroker.unsubscribe(eventHandler))
        return true;
    }

    return false;
  }

  /**
   * Unsubscribe
   *
   * @param requestEventDescriptor
   * @param function
   */
  @Override
  public <E, R> boolean unsubscribe(RequestEventDescriptor<E, R> requestEventDescriptor, Function<? super E, ? extends R> function)
  {
    Objects.requireNonNull(requestEventDescriptor, "requestEventDescriptor is null");
    Objects.requireNonNull(function, "function is null");

    if (DEBUG)
      printDebug("UNSUBSCRIBE", requestEventDescriptor);

    Map.Entry<RequestEventDescriptor<?, ?>, Function<?, ?>> entry = new AbstractMap.SimpleEntry<>(requestEventDescriptor, function);
    EventHandler eventHandler = requestEventDescriptorMap.remove(entry);
    if (eventHandler != null)
    {
      if (eventBroker.unsubscribe(eventHandler))
        return true;
    }

    return false;
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Get eventDescriptors
   */
  @Override
  public Stream<EventDescriptor<?>> getEventDescriptors()
  {
    Stream<EventDescriptor<?>> stream1 = eventDescriptorMap.keySet().stream().map(Entry::getKey);
    Stream<EventDescriptor<?>> stream2 = requestEventDescriptorMap.keySet().stream().map(Entry::getKey).map(RequestEventDescriptor::getRequestEventDescriptor);
    return Stream.concat(stream1, stream2).distinct().sorted(Comparator.comparing(Object::toString));
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Post a data
   *
   * @param eventDescriptor
   * @param data
   */
  @Override
  public <E> void post(EventDescriptor<E> eventDescriptor, E data)
  {
    Objects.requireNonNull(eventDescriptor, "eventDescriptor is null");

    // check
    checkSameEventTypeClass(eventDescriptor.getTopic(), eventDescriptor.getEventDescriptorClass());

    if (DEBUG)
      printDebug("POST", eventDescriptor, data);

    eventBroker.post(eventDescriptor.getTopic(), data);
  }

  /**
   * Send a data
   *
   * @param eventDescriptor
   * @param data
   */
  @Override
  public <E> void send(EventDescriptor<E> eventDescriptor, E data)
  {
    Objects.requireNonNull(eventDescriptor, "eventDescriptor is null");

    // check
    checkSameEventTypeClass(eventDescriptor.getTopic(), eventDescriptor.getEventDescriptorClass());

    if (DEBUG)
      printDebug("SEND", eventDescriptor, data);

    sendImpl(eventDescriptor, data, Long.MAX_VALUE, TimeUnit.DAYS);
  }

  /**
   * Send a data
   *
   * @param eventDescriptor
   * @param data
   * @param timeout
   * @param timeUnit
   */
  @Override
  public <E> void send(EventDescriptor<E> eventDescriptor, E data, long timeout, TimeUnit timeUnit)
  {
    Objects.requireNonNull(eventDescriptor, "eventDescriptor is null");
    if (timeout <= 0)
      throw new IllegalArgumentException("timeout is <= 0");
    Objects.requireNonNull(timeUnit, "timeUnit is null");

    // check
    checkSameEventTypeClass(eventDescriptor.getTopic(), eventDescriptor.getEventDescriptorClass());

    if (DEBUG)
      printDebug("SEND", eventDescriptor, data, timeout, timeUnit);

    sendImpl(eventDescriptor, data, timeout, timeUnit);
  }

  private <E> void sendImpl(EventDescriptor<E> eventDescriptor, E data, long timeout, TimeUnit timeUnit)
  {
    Set<String> set = new TreeSet<>();
    set.add(UIEvents.ALL_SUB_TOPICS);
    set.add(eventDescriptor.getTopic());
    String[] splitted = eventDescriptor.getTopic().split(UIEvents.TOPIC_SEP);
    StringBuilder topicBuffer = new StringBuilder(eventDescriptor.getTopic().length());
    for(final String split : splitted)
    {
      topicBuffer.append(split).append(UIEvents.TOPIC_SEP);
      String subscribedTopic = topicBuffer.toString() + UIEvents.ALL_SUB_TOPICS;
      set.add(subscribedTopic);
    }

    long total = eventDescriptorMap.keySet().stream()
      .map(Map.Entry::getKey)
      .filter(ed -> set.contains(ed.getTopic()) && acceptDataForEvent(data, ed.getEventDescriptorClass()))
      .count();

    CountDownLatch countDownLatch = new CountDownLatch((int) total);
    List<Exception> throwableList = new ArrayList<>();

    Map<String, Object> map = new HashMap<>();
    map.put(EventConstants.EVENT_TOPIC, eventDescriptor.getTopic());
    map.put(IEventBroker.DATA, data);
    map.put(COUNT_DOWN_LATCH_PROPERTY, countDownLatch);
    map.put(THROWABLE_LIST_PROPERTY, throwableList);
    eventBroker.send(eventDescriptor.getTopic(), map);

    try
    {
      boolean await = countDownLatch.await(timeout, timeUnit);
      if (!await)
      {
        String message = "Timeout after " + timeout + " " + timeUnit.toString().toLowerCase();
        throwableList.add(0, new TimeoutException(message));
      }
    }
    catch(InterruptedException e)
    {
      throwableList.add(e);
    }

    if (!throwableList.isEmpty())
      throw new EventException(throwableList);
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Post request
   * @param requestEventDescriptor
   * @param data
   * @param timeout
   * @param timeUnit
   * @param stopIf
   * @param consumer
   */
  @Override
  public <E, R> void postRequest(RequestEventDescriptor<E, R> requestEventDescriptor, E data,
    long timeout, TimeUnit timeUnit,
    BiPredicate<? super R, Throwable> stopIf,
    Consumer<CompletableFuture<List<? extends R>>> consumer)
  {
    Objects.requireNonNull(requestEventDescriptor, "requestEventDescriptor is null");
    if (timeout <= 0)
      throw new IllegalArgumentException("timeout is <= 0");
    Objects.requireNonNull(timeUnit, "timeUnit is null");
    Objects.requireNonNull(stopIf, "stopIf is null");
    Objects.requireNonNull(consumer, "consumer is null");

    // check
    checkSameEventTypeClass(requestEventDescriptor.getRequestEventDescriptor().getTopic(), requestEventDescriptor.getRequestEventDescriptor().getEventDescriptorClass());
    checkSameEventTypeClass(requestEventDescriptor.getReplyEventDescriptor().getTopic(), requestEventDescriptor.getReplyEventDescriptor().getEventDescriptorClass());

    if (DEBUG)
      printDebug("POST REQUEST", requestEventDescriptor, data, timeout, timeUnit);

    ReceiveResponsesConsumer<R> responseConsumer = new ReceiveResponsesConsumer<>(requestEventDescriptor, timeout, timeUnit, stopIf, consumer);
    responseConsumerSet.add(responseConsumer);

    EventDescriptor<E> eventDescriptor = requestEventDescriptor.getRequestEventDescriptor();

    eventBroker.post(eventDescriptor.getTopic(), data);
  }

  /**
   * Send request
   * @param requestEventDescriptor
   * @param data
   * @param timeout
   * @param timeUnit
   * @param stopIf
   * @param consumer
   */
  @Override
  public <E, R> void sendRequest(RequestEventDescriptor<E, R> requestEventDescriptor, E data,
    long timeout, TimeUnit timeUnit,
    BiPredicate<? super R, Throwable> stopIf,
    Consumer<CompletableFuture<List<? extends R>>> consumer)
  {
    Objects.requireNonNull(requestEventDescriptor, "requestEventDescriptor is null");
    if (timeout <= 0)
      throw new IllegalArgumentException("timeout is <= 0");
    Objects.requireNonNull(timeUnit, "timeUnit is null");
    Objects.requireNonNull(stopIf, "stopIf is null");
    Objects.requireNonNull(consumer, "consumer is null");

    // check
    checkSameEventTypeClass(requestEventDescriptor.getRequestEventDescriptor().getTopic(), requestEventDescriptor.getRequestEventDescriptor().getEventDescriptorClass());
    checkSameEventTypeClass(requestEventDescriptor.getReplyEventDescriptor().getTopic(), requestEventDescriptor.getReplyEventDescriptor().getEventDescriptorClass());

    if (DEBUG)
      printDebug("SEND REQUEST", requestEventDescriptor, data, timeout, timeUnit);

    ReceiveResponsesConsumer<R> responseConsumer = new ReceiveResponsesConsumer<>(requestEventDescriptor, timeout, timeUnit, stopIf, consumer);
    responseConsumerSet.add(responseConsumer);

    eventBroker.send(requestEventDescriptor.getRequestEventDescriptor().getTopic(), data);

    // waiting
    responseConsumer.await(timeout, timeUnit);
  }

  /**
   * @param topic
   * @param clazz
   */
  private void checkSameEventTypeClass(String topic, Class<?> clazz)
  {
    eventDescriptorMap.forEach((entry, eventHandler) -> {
      EventDescriptor<?> eventDescriptor = entry.getKey();
      if (eventDescriptor.getTopic().equals(topic))
      {
        Class<?> eventDescriptorClass = eventDescriptor.getEventDescriptorClass();
        if (!eventDescriptorClass.equals(clazz))
          throw new IllegalArgumentException("Topic '" + topic + "' already subscribed with class '" + eventDescriptorClass.getName() + "' but use class '" + clazz.getName() + "'");
      }
    });
  }

  /**
   * The class <b>ReceiveResponsesConsumer</b> allows to.<br>
   */
  private final class ReceiveResponsesConsumer<R> implements Consumer<R>
  {
    private final CompletableFuture<List<? extends R>> resultsCompletableFuture = new CompletableFuture<>();
    private final CompletableFuture<Object> waitingCompletableFuture = new CompletableFuture<>();
    private final List<R> responses = new LinkedList<>();
    private final RequestEventDescriptor<?, ? extends R> requestEventDescriptor;
    private ScheduledFuture<?> schedule;
    private final long countResponseEventDescriptor;
    private long currentCountResponseEventDescriptor;
    private long currentExceptionCountResponseEventDescriptor;
    private final BiPredicate<? super R, Throwable> stopIf;
    private final Set<Thread> currentThreads = ConcurrentHashMap.newKeySet();

    private ReceiveResponsesConsumer(RequestEventDescriptor<?, ? extends R> requestEventDescriptor,
      long timeout, TimeUnit timeUnit,
      BiPredicate<? super R, Throwable> stopIf,
      Consumer<CompletableFuture<List<? extends R>>> consumer)
    {
      this.requestEventDescriptor = requestEventDescriptor;
      this.stopIf = stopIf;
      if (consumer != null)
        consumer.accept(resultsCompletableFuture);

      // find
      countResponseEventDescriptor = requestEventDescriptorMap.keySet().stream()
        .map(Entry::getKey)
        .filter(requestEventDescriptor::equals)
        .count();

      //
      if (countResponseEventDescriptor == 0)
      {
        waitingCompletableFuture.complete("");
        resultsCompletableFuture.completeExceptionally(new RuntimeException("No response can be sent"));
        return;
      }

      // schedule for time out
      Runnable createExceptionTask = () -> {
        String message = "Timeout after " + timeout + " " + timeUnit.toString().toLowerCase() + " waiting for " + countResponseEventDescriptor + " response(s) of " + requestEventDescriptor.getReplyEventDescriptor();
        message += " but receive only " + responses.size() + " response(s)";
        if (currentExceptionCountResponseEventDescriptor != 0)
          message += " and " + currentExceptionCountResponseEventDescriptor + " exception(s)";
        completeExceptionally(new TimeoutException(message), false);
      };
      schedule = scheduledExecutorService.schedule(createExceptionTask, timeout, timeUnit);
    }

    @Override
    public synchronized void accept(R r)
    {
      if (resultsCompletableFuture.isDone())
        return;

      currentCountResponseEventDescriptor++;
      responses.add(r);
      if (currentCountResponseEventDescriptor + currentExceptionCountResponseEventDescriptor == countResponseEventDescriptor || stopIf.test(r, null))
      {
        schedule.cancel(false);
        if (responseConsumerSet.remove(this))
        {
          if (!resultsCompletableFuture.isCancelled())
          {
            resultsCompletableFuture.complete(responses);
            waitingCompletableFuture.complete("");
          }
        }
      }
    }

    private void removeThread(Thread currentThread)
    {
      currentThreads.remove(currentThread);
    }

    private void addThread(Thread currentThread)
    {
      currentThreads.add(currentThread);
    }

    private void await(long timeout, TimeUnit timeUnit)
    {
      waitingCompletableFuture.join();
      //      try
      //      {
      //        waitingCompletableFuture.get(timeout, timeUnit);
      //      }
      //      catch(InterruptedException | ExecutionException | TimeoutException e)
      //      {
      //        e.printStackTrace();
      //      }
    }

    private void completeExceptionally(Throwable throwable, boolean useStopIf)
    {
      if (resultsCompletableFuture.isDone())
        return;

      currentExceptionCountResponseEventDescriptor++;
      if (!responseConsumerSet.contains(this))
        return;

      if (!useStopIf || stopIf.test(null, throwable))
      {
        responseConsumerSet.remove(this);
        if (!resultsCompletableFuture.isCancelled())
        {
          resultsCompletableFuture.completeExceptionally(throwable);

          currentThreads.forEach(Thread::interrupt);
        }
        waitingCompletableFuture.complete("");
      }
      else if (currentCountResponseEventDescriptor + currentExceptionCountResponseEventDescriptor == countResponseEventDescriptor)
      {
        responseConsumerSet.remove(this);
        schedule.cancel(false);
        if (!resultsCompletableFuture.isCancelled())
          resultsCompletableFuture.complete(responses);
        waitingCompletableFuture.complete("");
      }
    }
  }

  private static boolean acceptDataForEvent(Object o, Class<?> clazz)
  {
    return o == null || Void.class.equals(clazz) || clazz.isInstance(o);
  }

  private static <E> void printDebug(String prefix, EventDescriptor<E> eventDescriptor, E data)
  {
    System.out.println(prefix + " eventDescriptor=" + eventDescriptor +
      ", data=" + data +
      ", call by=" + Thread.currentThread().getStackTrace()[3] +
      ", thread=" + Thread.currentThread());
  }

  private static <E> void printDebug(String prefix, EventDescriptor<E> eventDescriptor, E data, long timeout, TimeUnit timeUnit)
  {
    System.out.println(prefix + " eventDescriptor=" + eventDescriptor +
      ", data=" + data +
      ", timeout=" + timeout +
      ", timeUnit=" + timeUnit +
      ", call by=" + Thread.currentThread().getStackTrace()[3] +
      ", thread=" + Thread.currentThread());
  }

  private static <E> void printDebug(String prefix, EventDescriptor<E> eventDescriptor)
  {
    System.out.println(prefix + " eventDescriptor=" + eventDescriptor +
      ", call by=" + Thread.currentThread().getStackTrace()[3] +
      ", thread=" + Thread.currentThread());
  }

  private static <E, R> void printDebug(String prefix, RequestEventDescriptor<E, R> requestEventDescriptor)
  {
    System.out.println(prefix + " requestEventDescriptor=" + requestEventDescriptor +
      ", call by=" + Thread.currentThread().getStackTrace()[3] +
      ", thread=" + Thread.currentThread());
  }

  private static <E, R> void printDebug(String prefix, RequestEventDescriptor<E, R> requestEventDescriptor, E data, long timeout, TimeUnit timeUnit)
  {
    System.out.println(prefix + " requestEventDescriptor=" + requestEventDescriptor +
      ", data=" + data +
      ", timeout=" + timeout +
      ", timeUnit=" + timeUnit +
      ", call by=" + Thread.currentThread().getStackTrace()[3] +
      ", thread=" + Thread.currentThread());
  }

  @Override
  public void debugCall()
  {
    DEBUG = true;
  }

  @Override
  public void dump()
  {
    System.out.println("eventDescriptorMap " + eventDescriptorMap.size());
    System.out.println("requestEventDescriptorMap " + requestEventDescriptorMap.size());
    System.out.println("responseConsumerSet " + responseConsumerSet.size());
  }
}
