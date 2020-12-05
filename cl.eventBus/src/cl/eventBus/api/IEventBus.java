package cl.eventBus.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The interface <b>IEventBus</b> allows to.<br>
 */
public interface IEventBus
{
  /**
   * Return all descriptors
   */
  Stream<EventDescriptor<?>> getEventDescriptors();

  /////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Subscribe
   *
   * @param eventDescriptor
   * @param consumer
   */
  <E> boolean subscribe(EventDescriptor<E> eventDescriptor, Consumer<? super E> consumer);

  /**
   * Unsubscribe
   *
   * @param eventDescriptor
   * @param consumer
   */
  <E> boolean unsubscribe(EventDescriptor<E> eventDescriptor, Consumer<? super E> consumer);

  /////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Post a data
   *
   * @param eventDescriptor
   * @param data
   */
  <E> void post(EventDescriptor<E> eventDescriptor, E data);

  /**
   * Send a data
   *
   * @param eventDescriptor
   * @param data
   */
  <E> void send(EventDescriptor<E> eventDescriptor, E data) throws EventException;

  /**
   * Send a data
   *
   * @param eventDescriptor
   * @param data
   * @param timeout
   * @param timeUnit
   */
  <E> void send(EventDescriptor<E> eventDescriptor, E data, long timeout, TimeUnit timeUnit) throws EventException;

  /////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Subscribe
   *
   * @param requestEventDescriptor
   * @param function
   */
  <E, R> boolean subscribe(RequestEventDescriptor<E, R> requestEventDescriptor, Function<? super E, ? extends R> function);

  /**
   * Unsubscribe
   *
   * @param requestEventDescriptor
   * @param function
   */
  <E, R> boolean unsubscribe(RequestEventDescriptor<E, R> requestEventDescriptor, Function<? super E, ? extends R> function);

  /////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Post an event and receive responses
   *
   * @param requestEventDescriptor
   * @param data
   * @param timeout
   * @param timeUnit
   * @param stopIf
   * @param consumer
   */
  <E, R> void postRequest(RequestEventDescriptor<E, R> requestEventDescriptor, E data,
    long timeout, TimeUnit timeUnit,
    BiPredicate<? super R, Throwable> stopIf,
    Consumer<CompletableFuture<List<? extends R>>> consumer);

  /**
   * Send an event and receive responses
   *
   * @param requestEventDescriptor
   * @param data
   * @param returnEventDescriptor
   * @param timeout
   * @param timeUnit
   * @param consumer
   */
  <E, R> void sendRequest(RequestEventDescriptor<E, R> requestEventDescriptor, E data,
    long timeout, TimeUnit timeUnit,
    BiPredicate<? super R, Throwable> stopIf,
    Consumer<CompletableFuture<List<? extends R>>> consumer);

  // just for debug
  void debugCall();

  // just for debug
  void dump();
}
