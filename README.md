# EventBus
RCP Event bus v1.0.0 allows to post/send an event and receive some responses.<br>
Implementation use RCP EventBroker.

## Install
```
https://raw.githubusercontent.com/cylagarde/EventBus/master/cl.eventBus.update_site
```

## How to inject eventBus
```
import cl.eventBus.api.IEventBus;

@Inject
IEventBus eventBus;
```

## Define EventDescriptor
```
EventDescriptor<YourClass> MY_EVENT_DESCRIPTOR = new EventDescriptor<>("YourClass/topic", YourClass.class);
```

## Subscribe/Unsubscribe to EventDescriptor
```
Consumer<YourClass> consumer = instance -> System.out.println("receive "+instance);
eventBus.subscribe(MY_EVENT_DESCRIPTOR, consumer);
```

## Post/Send event to eventBus
```
YourClass instance = ...
// post: call not blocked
eventBus.post(MY_EVENT_DESCRIPTOR, instance);
// send: call blocked
eventBus.send(MY_EVENT_DESCRIPTOR, instance);
```

## Subscribe/Unsubscribe to request
```
EventDescriptor<String> RESPONSE_EVENT_DESCRIPTOR = new EventDescriptor<>("response/topic", String.class);
RequestEventDescriptor<YourClass, String> REQUEST_EVENT_DESCRIPTOR = new RequestEventDescriptor<>(MY_EVENT_DESCRIPTOR, RESPONSE_EVENT_DESCRIPTOR);

Function<YourClass, String> responseFunction = instance -> {
	if (valid(instance))
		return "OK";
	return "KO";
};
eventBus.subscribe(REQUEST_EVENT_DESCRIPTOR, responseFunction);
```

## Post/Send a request and wait responses with timeout
```
Consumer<CompletableFuture<List<String>>> consumer = completableFuture ->
  completableFuture.handle((responses, exception) -> {
	 if (exception != null)
	 	 treat exception
	 String firstResponse = responses.get(0);
	 if ("OK".equals(firstResponse))
	 	  treat validResponse
	 treat invalidResponse
  });
eventBus.postRequest(REQUEST_EVENT_DESCRIPTOR, instance,
        10, TimeUnit.SECONDS,
        (oneResponse, exception) -> true, // stop if receive a response or an exception from user or time out
        consumer);
eventBus.sendRequest(...);
```
