package com.bot_produktif;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class SampleController {
	
	@RequestMapping("/")
	public String HelloWorld()
	{
		return "Hello World";
	}

}