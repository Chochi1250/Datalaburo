package com.DataLaburo.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class H2ConsoleRedirectController {
	@GetMapping("/h2")
	public String h2() {
		return "redirect:/h2-console";
	}

	@GetMapping("/h2/")
	public String h2Slash() {
		return "redirect:/h2-console";
	}
}

