package com.myspring.framework.webmvc.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.myspring.framework.annotation.MyAutowired;
import com.myspring.framework.annotation.MyController;
import com.myspring.framework.annotation.MyRequestMapping;
import com.myspring.framework.annotation.MyRequestParam;
import com.myspring.framework.annotation.MyService;

public class MyDispatcherServlet extends HttpServlet {
	
	private Properties contextConfig = new Properties();
	
	private List<String> classNames = new ArrayList<String>(); 

	private Map<String, Object> ioc = new HashMap<String, Object>();
	
//	private Map<String, Method> handlerMapping = new HashMap<String, Method>();
	private List<Handler> handlerMapping = new ArrayList<Handler>();
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		this.doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		try {
			//6.等待请求
			doDispatcher(req, resp);
		}catch (Exception e) {
			e.printStackTrace();
			resp.getWriter().write("500 server inner error!");
		}
	}

//	private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws IOException {
//		
//		String url = req.getRequestURI();
//		String contextPath = req.getContextPath();
//		
//		url = url.replace(contextPath, "").replaceAll("/+", "/");
//		
//		if(!handlerMapping.containsKey(url)) {
//			resp.getWriter().write("404 Not Found!");
//			return;
//		}
//		Method method = handlerMapping.get(url);
//		System.out.println(method);
//		
//	}
	private void doDispatcher(HttpServletRequest req, HttpServletResponse resp) throws Exception {
		
		try {
			
			Handler handler = getHandler(req);
			
			if(handler == null) {
				resp.getWriter().write("404 Not Found!");
				return;
			}
			
			//获取方法的参数列表
			Class<?>[] parameterTypes = handler.method.getParameterTypes();
			//保存所有需要赋值的参数值
			Object[] parameterValues = new Object[parameterTypes.length];
			
			Map<String, String[]> parameterMap = req.getParameterMap();
			for(Map.Entry<String, String[]> param : parameterMap.entrySet()) {
				String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "").replaceAll(",\\s", ",");
				
				//如果找到匹配参数，则开始填充参数
				if(!handler.paramIndexMapping.containsKey(param.getKey())) {continue;}
				Integer index = handler.paramIndexMapping.get(param.getKey());
				
				parameterValues[index] = convert(parameterTypes[index], value);
				
			}
			
			//设置方法中的request对象和response对象
			Integer reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
			parameterValues[reqIndex] = req;
			Integer respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
			parameterValues[respIndex] = resp;
			
			
			handler.method.invoke(handler.controller, parameterValues);
			
			
		}catch(Exception e) {
			throw e;
		}
		
	}

	private Object convert(Class<?> type, String value) {
		if(Integer.class == type) {
			return Integer.valueOf(value);
		}
		return value;
	}

	private Handler getHandler(HttpServletRequest req) {
		
		if(handlerMapping.isEmpty()) {return null;}
		
		String url = req.getRequestURI();
		String contextPath = req.getContextPath();
		url = url.replace(contextPath, "").replaceAll("/+", "/");
		
		for(Handler handler : handlerMapping) {
			
			Matcher matcher = handler.pattern.matcher(url);
			
			if(!matcher.matches()) {continue;}
			
			return handler;
		}
		
		return null;
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		
		//1.加载配置文件
		doLoadConfig(config.getInitParameter("contextConfigLocation"));
		
		//2.扫描所有相关的内容
		doScanner(contextConfig.getProperty("scanPackage"));
		
		//3.初始化所有相关的类
		doInstance();
		
		//4.自动注入
		doAutowired();
		
		//5.初始化HandlerMapping
		initHandlerMapping();
		
		System.out.println("myspring init finished!");
		
	}

	private void initHandlerMapping() {
		
		if(ioc.isEmpty()) {return;}
		
		for(Map.Entry<String, Object> entry : ioc.entrySet()) {
			Class<?> clazz = entry.getValue().getClass();
			
			if(!clazz.isAnnotationPresent(MyController.class)) {continue;}
			
			String baseUrl = "";
			if(clazz.isAnnotationPresent(MyRequestMapping.class)) {
				MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
				baseUrl = requestMapping.value();
			}
			
			//扫描所有的公共方法
			Method[] methods = clazz.getMethods();
			for(Method method : methods) {
				if(!method.isAnnotationPresent(MyRequestMapping.class)) {continue;}
				MyRequestMapping requestMapping = method.getAnnotation(MyRequestMapping.class);
				
//				String methodUrl = ("/" + baseUrl + requestMapping.value()).replaceAll("/+", "/");//连续的/处理成一个/
//				handlerMapping.put(methodUrl, method);
				String regex = ("/" + baseUrl + requestMapping.value()).replaceAll("/+", "/");
				Pattern pattern = Pattern.compile(regex);
				handlerMapping.add(new Handler(entry.getValue(), method, pattern));
				
				System.out.println("Mapping : " + regex + ", " + method);
				
			}
			
		
		}
		
	}

	private void doAutowired() {
		
		if(ioc.isEmpty()) {return;}
		
		//循环ico所有的类，给需要自动赋值的属性自动赋值
		for(Map.Entry<String, Object> entry :ioc.entrySet()) {
			Field[] fields = entry.getValue().getClass().getDeclaredFields();
			
			for(Field field : fields) {
				if(!field.isAnnotationPresent(MyAutowired.class)) {continue;}
				
				MyAutowired autowired = field.getAnnotation(MyAutowired.class);
				
				String beanName = autowired.value().trim();
				
				if("".equals(beanName)) {
					beanName = field.getType().getName();
				}
				//
				field.setAccessible(true);
				
				try {
					//给指定对象上的字段上设置指定的值
					field.set(entry.getValue(), ioc.get(beanName));
				} catch (Exception e) {
					e.printStackTrace();
					continue;
				}
			}
		}
		
	}

	private void doInstance() {
		if(classNames.isEmpty()) {return;}
		try {
			for(String className : classNames) {
				Class<?> clazz = Class.forName(className);
				//不是所有的类都要实例化，只有加了自定义注解的才进行实例化
				if(clazz.isAnnotationPresent(MyController.class)) {
					//key 为类名首字母小写
					String beanName = lowerFirstCase(clazz.getName());
					ioc.put(beanName, clazz.newInstance());
					
				} else if(clazz.isAnnotationPresent(MyService.class)) {
					
					MyService service = clazz.getAnnotation(MyService.class);
					//2.自定义名字，优先使用自定义名字
					String beanName = service.value();
					if("".equals(beanName.trim())) {
						//1.默认采用首字母小写
						beanName = lowerFirstCase(clazz.getName());
					}
					Object instance = clazz.newInstance();
					ioc.put(beanName, instance);
					//3.根据接口类型来赋值
					for(Class<?> i : clazz.getInterfaces()) {
						ioc.put(i.getName(), instance);
					}
					
				} else {
					continue;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void doScanner(String scanPackage) {
		
		String path = scanPackage.replaceAll("\\.", "/");
		URL url = this.getClass().getClassLoader().getResource(path);
		File classDir = new File(url.getFile());
		
		for(File file :classDir.listFiles()) {
			if(file.isDirectory()) {
				doScanner(scanPackage+"."+file.getName());
			} else {
				String className = scanPackage + "." + file.getName().replace(".class", "");
				classNames.add(className);
			}
		}
		
	}

	private void doLoadConfig(String contextConfigLocation) {
		InputStream is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
		try {
			contextConfig.load(is);
		} catch (IOException e) {
			e.printStackTrace();
		}finally {
			if(null != is) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
	}
	
	private String lowerFirstCase(String str) {
		char[] charArray = str.toCharArray();
		charArray[0] += 32;
		return String.valueOf(charArray);
	}
	
	
	private class Handler{
		
		protected Object controller;//保存对应的实例
		protected Method method;//保存映射的方法
		protected Pattern pattern;
		protected Map<String, Integer> paramIndexMapping;//参数顺序
		
		
		public Handler(Object controller, Method method, Pattern pattern) {
			this.controller = controller;
			this.method = method;
			this.pattern = pattern;
			
			this.paramIndexMapping = new HashMap<String, Integer>();
			putParamIndexMapping(method);
		}


		private void putParamIndexMapping(Method method) {
			
			//提取方法中加了注解的参数
			Annotation[][] pa = method.getParameterAnnotations();
			for(int i = 0; i < pa.length; i++) {
				for(Annotation a : pa[i]) {
					if(a instanceof MyRequestParam) {
						String paramName = ((MyRequestParam) a).value();
						if(!"".equals(paramName.trim())) {
							paramIndexMapping.put(paramName, i);
						}
					}
				}
			}
			//提取方法中request，和response 参数
			Class<?>[] pt = method.getParameterTypes();
			for(int i = 0; i < pt.length; i++) {
				Class<?> type = pt[i];
				if(type == HttpServletRequest.class || type == HttpServletResponse.class) {
					paramIndexMapping.put(type.getName(), i);
				}
			}
			
		}
	}
	

}
