package cl.eventBus.api;

import java.util.Objects;

/**
 * The class <b>RequestEventDescriptor</b> allows to.<br>
 */
public final class RequestEventDescriptor<E, R>
{
  final EventDescriptor<E> requestEventDescriptor;
  final EventDescriptor<R> replyEventDescriptor;

  public RequestEventDescriptor(EventDescriptor<E> requestEventDescriptor, EventDescriptor<R> replyEventDescriptor)
  {
    Objects.requireNonNull(requestEventDescriptor, "requestEventDescriptor is null");
    Objects.requireNonNull(replyEventDescriptor, "replyEventDescriptor is null");
    this.requestEventDescriptor = requestEventDescriptor;
    this.replyEventDescriptor = replyEventDescriptor;
  }

  public EventDescriptor<E> getRequestEventDescriptor()
  {
    return requestEventDescriptor;
  }

  public EventDescriptor<R> getReplyEventDescriptor()
  {
    return replyEventDescriptor;
  }

  @Override
  public int hashCode()
  {
    return Objects.hash(replyEventDescriptor, requestEventDescriptor);
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    RequestEventDescriptor<?, ?> other = (RequestEventDescriptor<?, ?>) obj;
    return Objects.equals(replyEventDescriptor, other.replyEventDescriptor) && Objects.equals(requestEventDescriptor, other.requestEventDescriptor);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[request=" + requestEventDescriptor + ", reply=" + replyEventDescriptor + "]";
  }
}
