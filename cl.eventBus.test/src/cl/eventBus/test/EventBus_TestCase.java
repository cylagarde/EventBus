package cl.eventBus.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.e4.core.contexts.EclipseContextFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import cl.eventBus.api.EventDescriptor;
import cl.eventBus.api.IEventBus;
import cl.eventBus.api.RequestEventDescriptor;

/**
 * The class <b>EventBus_TestCase</b> allows to.<br>
 */
public class EventBus_TestCase
{
  static IEventBus eventBus;

  @BeforeClass
  public static void beforeClass()
  {
    Bundle bundle = FrameworkUtil.getBundle(EventBus_TestCase.class);
    BundleContext bundleContext = bundle.getBundleContext();
    IEclipseContext eclipseCtx = EclipseContextFactory.getServiceContext(bundleContext);
    eventBus = eclipseCtx.get(IEventBus.class);
  }

  static void pause(int delay)
  {
    try
    {
      Thread.sleep(delay);
    }
    catch(InterruptedException e)
    {
    }
  }

  @Test
  public void test_send()
  {
    EventDescriptor<String> eventDescriptor = new EventDescriptor<>("a/test", String.class);

    AtomicBoolean atomicBoolean = new AtomicBoolean(false);
    Consumer<String> consumer = txt -> {
      pause(200);
      atomicBoolean.set(true);
    };
    try
    {
      assertTrue(eventBus.subscribe(eventDescriptor, consumer));
      assertFalse(eventBus.subscribe(eventDescriptor, consumer));

      eventBus.send(eventDescriptor, "data");

      assertTrue(atomicBoolean.get());
    }
    finally
    {
      assertTrue(eventBus.unsubscribe(eventDescriptor, consumer));
      assertFalse(eventBus.unsubscribe(eventDescriptor, consumer));
    }
  }

  @Test
  public void test_post()
  {
    EventDescriptor<String> eventDescriptor = new EventDescriptor<>("a/test", String.class);

    AtomicBoolean atomicBoolean = new AtomicBoolean(false);
    Consumer<String> consumer = txt -> {
      pause(200);
      atomicBoolean.set(true);
    };
    try
    {
      assertTrue(eventBus.subscribe(eventDescriptor, consumer));
      assertFalse(eventBus.subscribe(eventDescriptor, consumer));

      eventBus.post(eventDescriptor, "data");

      assertFalse(atomicBoolean.get());
      pause(250);
      assertTrue(atomicBoolean.get());
    }
    finally
    {
      assertTrue(eventBus.unsubscribe(eventDescriptor, consumer));
      assertFalse(eventBus.unsubscribe(eventDescriptor, consumer));
    }
  }

  @Test
  public void test_send_multiple_subscribe()
  {
    EventDescriptor<String> eventDescriptor1 = new EventDescriptor<>("a/test", String.class);
    EventDescriptor<Object> eventDescriptor2 = new EventDescriptor<>("a/*", Object.class);
    EventDescriptor<Object> eventDescriptor3 = new EventDescriptor<>("*", Object.class);

    int delay = 200;

    AtomicBoolean atomic1Boolean = new AtomicBoolean(false);
    Consumer<String> consumer1 = txt -> {
      pause(delay);
      atomic1Boolean.set(true);
    };

    AtomicBoolean atomic2Boolean = new AtomicBoolean(false);
    Consumer<Object> consumer2 = txt -> {
      pause(delay);
      atomic2Boolean.set(true);
    };

    AtomicBoolean atomic3Boolean = new AtomicBoolean(false);
    Consumer<Object> consumer3 = txt -> {
      pause(delay);
      atomic3Boolean.set(true);
    };

    try
    {
      assertTrue(eventBus.subscribe(eventDescriptor1, consumer1));
      assertTrue(eventBus.subscribe(eventDescriptor2, consumer2));
      assertTrue(eventBus.subscribe(eventDescriptor3, consumer3));

      long time = System.currentTimeMillis();
      eventBus.send(eventDescriptor1, "data");
      time = System.currentTimeMillis() - time;
      assertTime(time, delay);

      assertTrue(atomic1Boolean.get());
      assertTrue(atomic2Boolean.get());
      assertTrue(atomic3Boolean.get());
    }
    finally
    {
      assertTrue(eventBus.unsubscribe(eventDescriptor1, consumer1));
      assertTrue(eventBus.unsubscribe(eventDescriptor2, consumer2));
      assertTrue(eventBus.unsubscribe(eventDescriptor3, consumer3));
    }
  }

  @Test
  public void test_send_multiple_consumer()
  {
    EventDescriptor<String> eventDescriptor1 = new EventDescriptor<>("a/test", String.class);

    int delay = 200;

    AtomicBoolean atomic1Boolean = new AtomicBoolean(false);
    Consumer<String> consumer1 = txt -> {
      pause(delay);
      atomic1Boolean.set(true);
    };

    AtomicBoolean atomic2Boolean = new AtomicBoolean(false);
    Consumer<Object> consumer2 = txt -> {
      pause(delay);
      atomic2Boolean.set(true);
    };

    AtomicBoolean atomic3Boolean = new AtomicBoolean(false);
    Consumer<Object> consumer3 = txt -> {
      pause(delay);
      atomic3Boolean.set(true);
    };

    try
    {
      assertTrue(eventBus.subscribe(eventDescriptor1, consumer1));
      assertTrue(eventBus.subscribe(eventDescriptor1, consumer2));
      assertTrue(eventBus.subscribe(eventDescriptor1, consumer3));

      long time = System.currentTimeMillis();
      eventBus.send(eventDescriptor1, "data");
      time = System.currentTimeMillis() - time;
      assertTime(time, delay);

      assertTrue(atomic1Boolean.get());
      assertTrue(atomic2Boolean.get());
      assertTrue(atomic3Boolean.get());
    }
    finally
    {
      assertTrue(eventBus.unsubscribe(eventDescriptor1, consumer1));
      assertTrue(eventBus.unsubscribe(eventDescriptor1, consumer2));
      assertTrue(eventBus.unsubscribe(eventDescriptor1, consumer3));
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  public void test_sendRequest_waiting_1_response_on_2subscribe()
  {
    EventDescriptor<String> askEventDescriptor = new EventDescriptor<>("a/request", String.class);
    EventDescriptor<String> replyEventDescriptor = new EventDescriptor<>("a/reply", String.class);
    RequestEventDescriptor<String, String> requestEventDescriptor = new RequestEventDescriptor<>(askEventDescriptor, replyEventDescriptor);

    int delay = 200;

    AtomicReference<String> reply1Reference = new AtomicReference<>();
    Function<String, String> function1 = text -> {
      pause(delay);
      String reply = "reply1 from " + text;
      reply1Reference.set(reply);
      return reply;
    };

    AtomicReference<String> reply2Reference = new AtomicReference<>();
    Function<String, String> function2 = text -> {
      pause(delay);
      String reply = "reply2 from " + text;
      reply2Reference.set(reply);
      return reply;
    };

    AtomicReference<List<? extends String>> listReference = new AtomicReference<>();
    AtomicReference<Throwable> exceptionReference = new AtomicReference<>();
    Consumer<CompletableFuture<List<? extends String>>> consumer = cf -> {
      cf = cf.handle((list, th) -> {
        if (list != null)
          listReference.set(list);
        else
          exceptionReference.set(th);
        return null;
      });
    };

    String DATA = "data";

    try
    {
      assertTrue(eventBus.subscribe(requestEventDescriptor, function1));
      assertTrue(eventBus.subscribe(requestEventDescriptor, function2));
      assertFalse(eventBus.subscribe(requestEventDescriptor, function2));

      long time = System.currentTimeMillis();
      eventBus.sendRequest(requestEventDescriptor, DATA, 1, TimeUnit.SECONDS, (r, th) -> true, consumer);
      time = System.currentTimeMillis() - time;
      assertTime(time, delay);

      // check
      String reply1 = reply1Reference.get();
      String reply2 = reply2Reference.get();

      List<? extends String> list = listReference.get();
      assertNotNull(list);
      assertEquals(1, list.size());
      assertTrue(list.contains(reply1) || list.contains(reply2));
      assertNull(exceptionReference.get());
    }
    finally
    {
      assertTrue(eventBus.unsubscribe(requestEventDescriptor, function1));
      assertTrue(eventBus.unsubscribe(requestEventDescriptor, function2));
      assertFalse(eventBus.unsubscribe(requestEventDescriptor, function2));
    }
  }

  @Test
  public void test_sendRequest_waiting_2_response_on_2subscribe()
  {
    EventDescriptor<String> askEventDescriptor = new EventDescriptor<>("a/request", String.class);
    EventDescriptor<String> replyEventDescriptor = new EventDescriptor<>("a/reply", String.class);
    RequestEventDescriptor<String, String> requestEventDescriptor = new RequestEventDescriptor<>(askEventDescriptor, replyEventDescriptor);

    int delay = 200;

    AtomicReference<String> reply1Reference = new AtomicReference<>();
    Function<String, String> function1 = text -> {
      pause(delay);
      String reply = "reply1 from " + text;
      reply1Reference.set(reply);
      return reply;
    };

    AtomicReference<String> reply2Reference = new AtomicReference<>();
    Function<String, String> function2 = text -> {
      pause(delay);
      String reply = "reply2 from " + text;
      reply2Reference.set(reply);
      return reply;
    };

    AtomicReference<List<? extends String>> listReference = new AtomicReference<>();
    AtomicReference<Throwable> exceptionReference = new AtomicReference<>();
    Consumer<CompletableFuture<List<? extends String>>> consumer = cf -> {
      cf = cf.handle((list, th) -> {
        if (list != null)
          listReference.set(list);
        else
          exceptionReference.set(th);
        return null;
      });
    };

    String DATA = "data";

    try
    {
      assertTrue(eventBus.subscribe(requestEventDescriptor, function1));
      assertTrue(eventBus.subscribe(requestEventDescriptor, function2));

      long time = System.currentTimeMillis();
      eventBus.sendRequest(requestEventDescriptor, DATA, 1, TimeUnit.SECONDS, (r, th) -> false, consumer);
      time = System.currentTimeMillis() - time;
      assertTime(time, delay);

      // check
      String reply1 = reply1Reference.get();
      assertNotNull(reply1);
      String reply2 = reply2Reference.get();
      assertNotNull(reply2);

      List<?> list = listReference.get();
      assertNotNull(list);
      assertEquals(2, list.size());
      assertTrue(list.contains(reply1));
      assertTrue(list.contains(reply2));
      assertNull(exceptionReference.get());
    }
    finally
    {
      assertTrue(eventBus.unsubscribe(requestEventDescriptor, function1));
      assertTrue(eventBus.unsubscribe(requestEventDescriptor, function2));
    }
  }

  @Test
  public void test_sendRequest_withTimeOut()
  {
    EventDescriptor<String> askEventDescriptor = new EventDescriptor<>("a/request", String.class);
    EventDescriptor<String> replyEventDescriptor = new EventDescriptor<>("a/reply", String.class);
    RequestEventDescriptor<String, String> requestEventDescriptor = new RequestEventDescriptor<>(askEventDescriptor, replyEventDescriptor);

    int delay = 1000;

    AtomicReference<String> reply1Reference = new AtomicReference<>();
    Function<String, String> function1 = text -> {
      pause(delay);
      String reply = "reply1 from " + text;
      reply1Reference.set(reply);
      return reply;
    };

    AtomicReference<String> reply2Reference = new AtomicReference<>();
    Function<String, String> function2 = text -> {
      pause(delay);
      String reply = "reply2 from " + text;
      reply2Reference.set(reply);
      return reply;
    };

    AtomicReference<List<? extends String>> listReference = new AtomicReference<>();
    AtomicReference<Throwable> exceptionReference = new AtomicReference<>();
    Consumer<CompletableFuture<List<? extends String>>> consumer = cf -> {
      cf = cf.handle((list, th) -> {
        if (list != null)
          listReference.set(list);
        else
          exceptionReference.set(th);
        return null;
      });
    };

    String DATA = "data";

    try
    {
      assertTrue(eventBus.subscribe(requestEventDescriptor, function1));
      assertTrue(eventBus.subscribe(requestEventDescriptor, function2));

      long time = System.currentTimeMillis();
      eventBus.sendRequest(requestEventDescriptor, DATA, 200, TimeUnit.MILLISECONDS, (r, th) -> false, consumer);
      time = System.currentTimeMillis() - time;
      assertTime(time, delay);

      // check
      List<?> list = listReference.get();
      assertNull(list);
      Throwable throwable = exceptionReference.get();
      assertNotNull(throwable);
      assertTrue(throwable instanceof TimeoutException);
    }
    finally
    {
      assertTrue(eventBus.unsubscribe(requestEventDescriptor, function1));
      assertTrue(eventBus.unsubscribe(requestEventDescriptor, function2));
    }
  }

  @Test
  public void test_sendRequest_withException()
  {
    EventDescriptor<String> askEventDescriptor = new EventDescriptor<>("a/request", String.class);
    EventDescriptor<String> replyEventDescriptor = new EventDescriptor<>("a/reply", String.class);
    RequestEventDescriptor<String, String> requestEventDescriptor = new RequestEventDescriptor<>(askEventDescriptor, replyEventDescriptor);

    int delay = 100;

    class SpecialException extends RuntimeException
    {
      private static final long serialVersionUID = 1L;
    }

    AtomicReference<String> reply1Reference = new AtomicReference<>();
    Function<String, String> function1 = text -> {
      pause(2 * delay);
      String reply = "reply1 from " + text;
      reply1Reference.set(reply);
      return reply;
    };

    Function<String, String> function2 = text -> {
      pause(delay);
      throw new SpecialException();
    };

    AtomicReference<List<? extends String>> listReference = new AtomicReference<>();
    AtomicReference<Throwable> exceptionReference = new AtomicReference<>();
    Consumer<CompletableFuture<List<? extends String>>> consumer = cf -> {
      cf = cf.handle((list, th) -> {
        if (list != null)
          listReference.set(list);
        else
          exceptionReference.set(th);
        return null;
      });
    };

    String DATA = "data";

    try
    {
      assertTrue(eventBus.subscribe(requestEventDescriptor, function1));
      assertTrue(eventBus.subscribe(requestEventDescriptor, function2));

      long time = System.currentTimeMillis();
      eventBus.sendRequest(requestEventDescriptor, DATA, 1000, TimeUnit.SECONDS, (r, th) -> r != null, consumer);
      time = System.currentTimeMillis() - time;
      assertTime(time, 2 * delay);

      // check
      List<?> list = listReference.get();
      assertNotNull(list);
      assertEquals(1, list.size());
      assertNull(exceptionReference.get());

      // init
      listReference.set(null);

      // stop with first exception
      time = System.currentTimeMillis();
      eventBus.sendRequest(requestEventDescriptor, DATA, 1, TimeUnit.SECONDS, (r, th) -> th != null, consumer);
      time = System.currentTimeMillis() - time;
      assertTime(time, delay);

      // check
      list = listReference.get();
      assertNull(list);
      Throwable throwable = exceptionReference.get();
      assertNotNull(throwable);
      assertTrue(throwable instanceof SpecialException);

    }
    finally
    {
      assertTrue(eventBus.unsubscribe(requestEventDescriptor, function1));
      assertTrue(eventBus.unsubscribe(requestEventDescriptor, function2));
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////

  @Test
  public void test_postRequest_waiting_1_response_on_2subscribe()
  {
    EventDescriptor<String> askEventDescriptor = new EventDescriptor<>("a/request", String.class);
    EventDescriptor<String> replyEventDescriptor = new EventDescriptor<>("a/reply", String.class);
    RequestEventDescriptor<String, String> requestEventDescriptor = new RequestEventDescriptor<>(askEventDescriptor, replyEventDescriptor);

    int delay = 200;

    AtomicReference<String> reply1Reference = new AtomicReference<>();
    Function<String, String> function1 = text -> {
      pause(delay);
      String reply = "reply1 from " + text;
      reply1Reference.set(reply);
      return reply;
    };

    AtomicReference<String> reply2Reference = new AtomicReference<>();
    Function<String, String> function2 = text -> {
      pause(delay);
      String reply = "reply2 from " + text;
      reply2Reference.set(reply);
      return reply;
    };

    AtomicReference<List<? extends String>> listReference = new AtomicReference<>();
    AtomicReference<Throwable> exceptionReference = new AtomicReference<>();
    Consumer<CompletableFuture<List<? extends String>>> consumer = cf -> {
      cf = cf.handle((list, th) -> {
        if (list != null)
          listReference.set(list);
        else
          exceptionReference.set(th);
        return null;
      });
    };

    String DATA = "data";

    try
    {
      assertTrue(eventBus.subscribe(requestEventDescriptor, function1));
      assertTrue(eventBus.subscribe(requestEventDescriptor, function2));
      assertFalse(eventBus.subscribe(requestEventDescriptor, function2));

      long time = System.currentTimeMillis();
      eventBus.postRequest(requestEventDescriptor, DATA, 1, TimeUnit.SECONDS, (r, th) -> true, consumer);
      time = System.currentTimeMillis() - time;
      assertTime(time, 0);

      assertNull(reply1Reference.get());
      assertNull(reply2Reference.get());
      assertNull(listReference.get());

      pause(delay + 50);

      // check
      String reply1 = reply1Reference.get();
      String reply2 = reply2Reference.get();

      List<?> list = listReference.get();
      assertNotNull(list);
      assertEquals(1, list.size());
      assertTrue(list.contains(reply1) || list.contains(reply2));
      assertNull(exceptionReference.get());
    }
    finally
    {
      assertTrue(eventBus.unsubscribe(requestEventDescriptor, function1));
      assertTrue(eventBus.unsubscribe(requestEventDescriptor, function2));
      assertFalse(eventBus.unsubscribe(requestEventDescriptor, function2));
    }
  }

  @Test
  public void test_postRequest_waiting_2_response_on_2subscribe()
  {
    EventDescriptor<String> askEventDescriptor = new EventDescriptor<>("a/request", String.class);
    EventDescriptor<String> replyEventDescriptor = new EventDescriptor<>("a/reply", String.class);
    RequestEventDescriptor<String, String> requestEventDescriptor = new RequestEventDescriptor<>(askEventDescriptor, replyEventDescriptor);

    int delay = 200;

    AtomicReference<String> reply1Reference = new AtomicReference<>();
    Function<String, String> function1 = text -> {
      pause(delay);
      String reply = "reply1 from " + text;
      reply1Reference.set(reply);
      return reply;
    };

    AtomicReference<String> reply2Reference = new AtomicReference<>();
    Function<String, String> function2 = text -> {
      pause(delay);
      String reply = "reply2 from " + text;
      reply2Reference.set(reply);
      return reply;
    };

    AtomicReference<List<? extends String>> listReference = new AtomicReference<>();
    AtomicReference<Throwable> exceptionReference = new AtomicReference<>();
    Consumer<CompletableFuture<List<? extends String>>> consumer = cf -> {
      cf = cf.handle((list, th) -> {
        if (list != null)
          listReference.set(list);
        else
          exceptionReference.set(th);
        return null;
      });
    };

    String DATA = "data";

    try
    {
      assertTrue(eventBus.subscribe(requestEventDescriptor, function1));
      assertTrue(eventBus.subscribe(requestEventDescriptor, function2));

      long time = System.currentTimeMillis();
      eventBus.postRequest(requestEventDescriptor, DATA, 1, TimeUnit.SECONDS, (r, th) -> false, consumer);
      time = System.currentTimeMillis() - time;
      assertTime(time, 0);

      assertNull(reply1Reference.get());
      assertNull(reply2Reference.get());
      assertNull(listReference.get());

      pause(delay + 50);

      // check
      String reply1 = reply1Reference.get();
      assertNotNull(reply1);
      String reply2 = reply2Reference.get();
      assertNotNull(reply2);

      List<?> list = listReference.get();
      assertNotNull(list);
      assertEquals(2, list.size());
      assertTrue(list.contains(reply1));
      assertTrue(list.contains(reply2));
      assertNull(exceptionReference.get());
    }
    finally
    {
      assertTrue(eventBus.unsubscribe(requestEventDescriptor, function1));
      assertTrue(eventBus.unsubscribe(requestEventDescriptor, function2));
    }
  }

  @Test
  public void test_postRequest_withTimeOut()
  {
    EventDescriptor<String> askEventDescriptor = new EventDescriptor<>("a/request", String.class);
    EventDescriptor<String> replyEventDescriptor = new EventDescriptor<>("a/reply", String.class);
    RequestEventDescriptor<String, String> requestEventDescriptor = new RequestEventDescriptor<>(askEventDescriptor, replyEventDescriptor);

    int delay = 1000;

    AtomicReference<String> reply1Reference = new AtomicReference<>();
    Function<String, String> function1 = text -> {
      pause(delay);
      String reply = "reply1 from " + text;
      reply1Reference.set(reply);
      return reply;
    };

    AtomicReference<String> reply2Reference = new AtomicReference<>();
    Function<String, String> function2 = text -> {
      pause(delay);
      String reply = "reply2 from " + text;
      reply2Reference.set(reply);
      return reply;
    };

    AtomicReference<List<? extends String>> listReference = new AtomicReference<>();
    AtomicReference<Throwable> exceptionReference = new AtomicReference<>();
    Consumer<CompletableFuture<List<? extends String>>> consumer = cf -> {
      cf = cf.handle((list, th) -> {
        if (list != null)
          listReference.set(list);
        else
          exceptionReference.set(th);
        return null;
      });
    };

    String DATA = "data";

    try
    {
      assertTrue(eventBus.subscribe(requestEventDescriptor, function1));
      assertTrue(eventBus.subscribe(requestEventDescriptor, function2));

      long time = System.currentTimeMillis();
      eventBus.postRequest(requestEventDescriptor, DATA, 200, TimeUnit.MILLISECONDS, (r, th) -> false, consumer);
      time = System.currentTimeMillis() - time;
      assertTime(time, 0);

      assertNull(reply1Reference.get());
      assertNull(reply2Reference.get());
      assertNull(listReference.get());

      pause(delay + 50);

      // check
      List<?> list = listReference.get();
      assertNull(list);
      Throwable throwable = exceptionReference.get();
      assertNotNull(throwable);
      assertTrue(throwable instanceof TimeoutException);
    }
    finally
    {
      assertTrue(eventBus.unsubscribe(requestEventDescriptor, function1));
      assertTrue(eventBus.unsubscribe(requestEventDescriptor, function2));
    }
  }

  @Test
  public void test_postRequest_withException()
  {
    EventDescriptor<String> askEventDescriptor = new EventDescriptor<>("a/request", String.class);
    EventDescriptor<String> replyEventDescriptor = new EventDescriptor<>("a/reply", String.class);
    RequestEventDescriptor<String, String> requestEventDescriptor = new RequestEventDescriptor<>(askEventDescriptor, replyEventDescriptor);

    int delay = 100;

    class SpecialException extends RuntimeException
    {
      private static final long serialVersionUID = 1L;
    }

    AtomicReference<String> reply1Reference = new AtomicReference<>();
    Function<String, String> function1 = text -> {
      pause(2 * delay);
      String reply = "reply1 from " + text;
      reply1Reference.set(reply);
      return reply;
    };

    Function<String, String> function2 = text -> {
      pause(delay);
      throw new SpecialException();
    };

    AtomicReference<List<? extends String>> listReference = new AtomicReference<>();
    AtomicReference<Throwable> exceptionReference = new AtomicReference<>();
    Consumer<CompletableFuture<List<? extends String>>> consumer = cf -> {
      cf = cf.handle((list, th) -> {
        if (list != null)
          listReference.set(list);
        else
          exceptionReference.set(th);
        return null;
      });
    };

    String DATA = "data";

    try
    {
      assertTrue(eventBus.subscribe(requestEventDescriptor, function1));
      assertTrue(eventBus.subscribe(requestEventDescriptor, function2));

      long time = System.currentTimeMillis();
      eventBus.postRequest(requestEventDescriptor, DATA, 1000, TimeUnit.SECONDS, (r, th) -> r != null, consumer);
      time = System.currentTimeMillis() - time;
      assertTime(time, 0);

      assertNull(reply1Reference.get());
      assertNull(listReference.get());

      pause(2 * delay + 50);

      // check
      List<?> list = listReference.get();
      assertNotNull(list);
      assertEquals(1, list.size());
      assertNull(exceptionReference.get());

      // init
      listReference.set(null);

      // stop with first exception
      time = System.currentTimeMillis();
      eventBus.postRequest(requestEventDescriptor, DATA, 1, TimeUnit.SECONDS, (r, th) -> th != null, consumer);
      time = System.currentTimeMillis() - time;
      assertTime(time, 0);

      pause(delay + 50);

      // check
      list = listReference.get();
      assertNull(list);
      Throwable throwable = exceptionReference.get();
      assertNotNull(throwable);
      assertTrue(throwable instanceof SpecialException);

    }
    finally
    {
      assertTrue(eventBus.unsubscribe(requestEventDescriptor, function1));
      assertTrue(eventBus.unsubscribe(requestEventDescriptor, function2));
    }
  }

  private static void assertTime(long time, int maxDelay)
  {
    assertTrue("time=" + time + " must be < " + (maxDelay + 50), time < maxDelay + 50);
  }
}
