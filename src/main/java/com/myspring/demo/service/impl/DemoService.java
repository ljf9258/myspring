package com.myspring.demo.service.impl;

import com.myspring.demo.service.IDemoService;
import com.myspring.framework.annotation.MyService;

@MyService
public class DemoService implements IDemoService {

	@Override
	public String get(String name) {
		return "demoService get : " +name;
	}

	@Override
	public String add(Integer a, Integer b) {
		return "demoService add : " +a +"," + b;
	}


}
