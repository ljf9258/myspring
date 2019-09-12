package com.myspring.demo.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.myspring.demo.service.IDemoService;
import com.myspring.framework.annotation.MyAutowired;
import com.myspring.framework.annotation.MyController;
import com.myspring.framework.annotation.MyRequestMapping;
import com.myspring.framework.annotation.MyRequestParam;

@MyController
@MyRequestMapping("/demo")
public class DemoController {
	
	@MyAutowired
	private IDemoService demoService;
	
	@MyRequestMapping("/get")
	public void query(HttpServletRequest request, HttpServletResponse response, @MyRequestParam("name")String name) {
		
		String result = demoService.get(name);
		
		try {
			response.getWriter().write(result);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	@MyRequestMapping("/add")
	public void add(HttpServletRequest request, HttpServletResponse response, @MyRequestParam("a")Integer a, @MyRequestParam("b")Integer b) {
		
		String result = demoService.add(a, b);
		
		try {
			response.getWriter().write(result);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	
}
