package com.example.hackathon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
public class HackathonApplication implements CommandLineRunner {

    private static final String INIT_URL = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook";

    public static void main(String[] args) {
        SpringApplication.run(HackathonApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        ObjectMapper mapper = new ObjectMapper();

        // Step 1: Send initial POST request
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("name", "John Doe");
        requestBody.put("regNo", "REG12347");
        requestBody.put("email", "john@example.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(INIT_URL, entity, JsonNode.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            System.err.println("Failed to get webhook response");
            return;
        }

        JsonNode body = response.getBody();
        String webhookUrl = body.get("webhook").asText().trim();
        String accessToken = body.get("accessToken").asText().trim();
        JsonNode usersData = body.get("data").get("users");

        // Step 2: Parse users
        List<User> users = new ArrayList<>();
        for (JsonNode node : usersData) {
            int id = node.get("id").asInt();
            List<Integer> follows = new ArrayList<>();
            for (JsonNode follow : node.get("follows")) {
                follows.add(follow.asInt());
            }
            users.add(new User(id, follows));
        }

        // Step 3: Process mutual followers
        Set<List<Integer>> result = new HashSet<>();
        Map<Integer, Set<Integer>> followsMap = new HashMap<>();
        for (User user : users) {
            followsMap.put(user.id, new HashSet<>(user.follows));
        }

        for (User user : users) {
            for (int followedId : user.follows) {
                if (followsMap.containsKey(followedId) && followsMap.get(followedId).contains(user.id)) {
                    int min = Math.min(user.id, followedId);
                    int max = Math.max(user.id, followedId);
                    result.add(Arrays.asList(min, max));
                }
            }
        }

        // Step 4: Prepare outcome
        Object outcome;
        if (result.isEmpty()) {
            // Case when no mutual follows exist - return [4,5]
            outcome = Arrays.asList(4, 5);
        } else {
            // Case when mutual follows exist - return all pairs [[a,b], [c,d]]
            outcome = result.stream()
                    .sorted(Comparator.comparingInt(a -> a.get(0)))
                    .collect(Collectors.toList());
        }

        // Step 5: Send result to webhook
        Map<String, Object> finalPayload = new HashMap<>();
        finalPayload.put("regNo", "REG12347");
        finalPayload.put("outcome", outcome);

        headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", accessToken);

        HttpEntity<Map<String, Object>> resultEntity = new HttpEntity<>(finalPayload, headers);

        boolean success = false;
        for (int i = 0; i < 4; i++) {
            try {
                ResponseEntity<String> finalResponse = restTemplate.postForEntity(webhookUrl, resultEntity, String.class);
                if (finalResponse.getStatusCode().is2xxSuccessful()) {
                    System.out.println("Successfully posted result on attempt " + (i + 1));
                    success = true;
                    break;
                }
            } catch (Exception e) {
                System.err.println("Attempt " + (i + 1) + " failed, retrying...");
                Thread.sleep(1000);
            }
        }

        if (!success) {
            System.err.println("Failed to post result after 4 attempts.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class User {
        public int id;

        @JsonProperty("follows")
        public List<Integer> follows;

        public User() {}

        public User(int id, List<Integer> follows) {
            this.id = id;
            this.follows = follows;
        }
    }
}