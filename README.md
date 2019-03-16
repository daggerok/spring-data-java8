# spring-data-java8
Java 8 CompletableFuture, Stream API return types for spring-data repositories

```java
@Repository
interface MessageRepository extends JpaRepository<Message, UUID> {

  Streamable<Message> streamAllBy();

  /*
     must be executed inside transaction, otherwise you will get this error:

     org.springframework.dao.InvalidDataAccessApiUsageException: You're trying to execute a streaming
     query method without a surrounding transaction that keeps the connection open so that the Stream can
     actually be consumed. Make sure the code consuming the stream uses @Transactional or any other way of
     declaring a (read-only) transaction.

     you can use TransactionTemplate for that, like so:

     return transactionTemplate.execute(status -> {
       try (Stream<Message> stream = messageRepository.findAnyBy()) {
         return stream.collect(toList());
       }
     });
  */
  Stream<Message> findAnyBy();

  /*
     you may want to configure and use custom taskExecutor:

     @Bean
     @Qualifier("myFutures")
     public TaskExecutor taskExecutor() {
       return new SimpleAsyncTaskExecutor("my-futures");
     }
  */
  @Async("myFutures")
  CompletableFuture<Iterable<Message>> findAllBy();
}
```

links:

- [read more](https://docs.spring.io/spring-data/data-jpa/docs/2.2.x/reference/html)
