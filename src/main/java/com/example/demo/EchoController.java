package com.example.demo;

import org.springframework.web.bind.annotation.*;

@RestController
public class EchoController {
    @PostMapping("/echo")
    public String echo(@RequestBody String input) {
        return "Received: " + input;
    }

    @GetMapping("/echo")
    public String echoGet(@RequestParam(defaultValue = "World") String input) {
        return "Received: " + input;
    }
}
