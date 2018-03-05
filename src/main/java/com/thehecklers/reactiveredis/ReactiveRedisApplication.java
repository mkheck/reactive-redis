package com.thehecklers.reactiveredis;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.UUID;

@SpringBootApplication
public class ReactiveRedisApplication {
    @Bean
    ReactiveRedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }

    @Bean
    ReactiveRedisOperations<String, Coffee> redisOperations(ReactiveRedisConnectionFactory factory) {
        Jackson2JsonRedisSerializer<Coffee> serializer = new Jackson2JsonRedisSerializer<>(Coffee.class);

        RedisSerializationContext.RedisSerializationContextBuilder<String, Coffee> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());

        RedisSerializationContext<String, Coffee> context = builder.value(serializer).build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    CommandLineRunner demoData(ReactiveRedisConnectionFactory factory, ReactiveRedisOperations<String, Coffee> coffeeOps) {
        return args -> {
            factory.getReactiveConnection().serverCommands().flushAll().thenMany(
                    Flux.just("Jet Black Redis", "Darth Redis", "Black Alert Redis")
                        .map(name -> new Coffee(UUID.randomUUID().toString(), name))
                        .flatMap(coffee -> coffeeOps.opsForValue().set(coffee.getId(), coffee)))
                    .thenMany(coffeeOps.keys("*")
                        .flatMap(coffeeOps.opsForValue()::get))
                    .subscribe(System.out::println);
        };
    }

    public static void main(String[] args) {
        SpringApplication.run(ReactiveRedisApplication.class, args);
    }
}

@RestController
class RedisController {
    private final ReactiveRedisOperations<String, Coffee> coffeeOps;

    RedisController(ReactiveRedisOperations<String, Coffee> coffeeOps) {
        this.coffeeOps = coffeeOps;
    }

    @GetMapping(value = "/coffees", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Coffee> getAllCoffees() {
        return coffeeOps.keys("*")
                .flatMap(coffeeOps.opsForValue()::get)
                .delayElements(Duration.ofSeconds(1));
    }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class Coffee {
    private String id;
    private String name;
}