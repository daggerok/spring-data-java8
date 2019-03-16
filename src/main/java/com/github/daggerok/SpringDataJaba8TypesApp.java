package com.github.daggerok;

import lombok.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.util.Streamable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.persistence.*;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PROTECTED;

@Data
@Entity
@NoArgsConstructor(access = PROTECTED)
@RequiredArgsConstructor(staticName = "of")
class Message {

  @Id
  @GeneratedValue
  @Setter(PRIVATE)
  UUID id;

  @NonNull
  @Setter(PRIVATE)
  @Basic(optional = false)
  @Column(nullable = false)
  String message;
}

@Repository
interface MessageRepository extends JpaRepository<Message, UUID> {

  @Async("myFutures")
  CompletableFuture<Iterable<Message>> findAllBy();

  Streamable<Message> streamAllBy();

  // must be executed inside transaction, otherwise you will get this error:
  //
  // org.springframework.dao.InvalidDataAccessApiUsageException: You're trying to execute a streaming
  // query method without a surrounding transaction that keeps the connection open so that the Stream can
  // actually be consumed. Make sure the code consuming the stream uses @Transactional or any other way of
  // declaring a (read-only) transaction.
  Stream<Message> findAnyBy();
}

@SpringBootApplication
public class SpringDataJaba8TypesApp {

  public static void main(String[] args) {
    SpringApplication.run(SpringDataJaba8TypesApp.class, args);
  }

  @Bean
  @Qualifier("myFutures")
  public TaskExecutor taskExecutor() {
    return new SimpleAsyncTaskExecutor("my-futures");
  }

  @Log4j2
  @RestController
  @RequiredArgsConstructor
  public static class RestResource {

    final MessageRepository messageRepository;

    private Message createMessage(Map<String, String> request) {
      String msg = Objects.requireNonNull(request.get("msg"), "mst may not be null");
      Message message = messageRepository.save(Message.of(msg));
      log.info("message saved: {}", () -> message);
      return message;
    }

    @Qualifier("myFutures")
    final TaskExecutor taskExecutor;

    @PostMapping
    public CompletableFuture<Message> post(@RequestBody Map<String, String> request) {
      return CompletableFuture.supplyAsync(() -> createMessage(request), taskExecutor);
    }

    @GetMapping("/future")
    public CompletableFuture<Iterable<Message>> getFuture() {
      return messageRepository.findAllBy();
    }

    @GetMapping("/stream")
    public Iterable<Message> getStream() {
      @Cleanup Stream<Message> stream = messageRepository.streamAllBy().stream();
      return stream.peek(log::info).collect(toList());
    }

    final TransactionTemplate transactionTemplate;

    @PostConstruct
    public void setTransactionReadOnly() {
      transactionTemplate.setReadOnly(true);
    }

    private Iterable<Message> findMessageStream() {
      return transactionTemplate.execute(status -> {
        try (Stream<Message> stream = messageRepository.findAnyBy()) {
          return stream.peek(log::info).collect(toList());
        }
      });
    }

    @RequestMapping
    public CompletableFuture<Iterable<Message>> get() {
      return CompletableFuture.supplyAsync(this::findMessageStream, taskExecutor);
    }

    /*
    @RequestMapping
    public Iterable<Message> get() {
      return findMessageStream();
    }

    @Transactional(readOnly = true) // required because we are using Stream
    public Iterable<Message> findMessageStream() {
      @Cleanup Stream<Message> stream = messageRepository.findAnyBy();
      return stream.collect(Collectors.toList());
    }
    */
  }
}
