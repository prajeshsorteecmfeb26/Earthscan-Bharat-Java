package com.earthscan.forum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Community Q&amp;A service, backed by MongoDB.
 *
 * <p><strong>Why this service uses a document store while the other two use MySQL:</strong> a forum
 * thread is read as a whole — post plus every reply, in order — and essentially never joined against
 * anything else. In the relational version that was two tables and a join on every page load, with
 * the comment rows existing only to be reassembled into their parent. Embedding replies in the post
 * document turns the entire feed into one query with no joins, and the write pattern (append a reply
 * to the end of an array) is exactly what a document model does well.</p>
 *
 * <p>The trade-off is real and worth stating: MongoDB caps a document at 16MB, so a thread cannot
 * grow without bound, and there are no cross-document transactions in play here. Both are acceptable
 * for a Q&amp;A thread, which is short-lived and self-contained. Neither would be acceptable for the
 * land or user data, which is why those stayed relational.</p>
 */
@SpringBootApplication(scanBasePackages = "com.earthscan")
@EnableDiscoveryClient
public class ForumServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForumServiceApplication.class, args);
    }
}
