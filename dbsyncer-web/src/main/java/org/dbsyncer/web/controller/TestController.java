package org.dbsyncer.web.controller;

import org.dbsyncer.biz.MappingService;
import org.dbsyncer.common.util.RandomUtil;
import org.dbsyncer.common.util.StringUtil;
import org.dbsyncer.web.remote.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.HandlerMethodArgumentResolverComposite;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.support.RequestContextUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequestMapping("/test")
public class TestController implements InitializingBean {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private UserService userService;

    @Autowired
    private MappingService mappingService;

    @ResponseBody
    @RequestMapping("/demo")
    public String demo(ModelMap modelMap, Long id, String version) {
        logger.info("id:{},version:{}", id, version);
        modelMap.put("data", RandomUtil.nextInt(1, 100));
        return id + version;
    }

    @ResponseBody
    @RequestMapping("/adapter.json")
    public Object adapter(HttpServletRequest request, HttpServletResponse response) {
        try {
            InvocableHandlerMethod invocableMethod = handlers.get("/test/demo");
            // ????????????
            Map<String, Object> params = new HashMap<>();
            params.put("id", 1000L);
            params.put("version", "20201124");

            // ??????????????????
            List<Object> providedArgs = new ArrayList<>();
            String[] parameterNames = parameterNameDiscoverer.getParameterNames(invocableMethod.getMethod());
            logger.info(Arrays.toString(parameterNames));
            if (!ObjectUtils.isEmpty(parameterNames)) {
                int length = parameterNames.length;
                for (int i = 0; i < length; i++) {
                    providedArgs.add(params.get(parameterNames[i]));
                }
            }

            ServletWebRequest webRequest = new ServletWebRequest(request, response);
            ModelAndViewContainer mavContainer = new ModelAndViewContainer();
            mavContainer.addAllAttributes(RequestContextUtils.getInputFlashMap(request));
            Object invoke = invocableMethod.invokeForRequest(webRequest, mavContainer, providedArgs.toArray());
            return invoke;
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    @Autowired
    private RequestMappingHandlerAdapter requestMappingHandlerAdapter;
    @Autowired
    private ApplicationContext applicationContext;
    private Map<String, String> parsePackage = new HashMap<>();
    private Map<String, InvocableHandlerMethod> handlers = new ConcurrentHashMap<>();
    private HandlerMethodArgumentResolverComposite resolvers = new HandlerMethodArgumentResolverComposite();
    private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Override
    public void afterPropertiesSet() {
        // ???????????????mapping
        initHandlerMapping();
        // ???????????????
        resolvers.addResolvers(requestMappingHandlerAdapter.getArgumentResolvers());
    }

    private void initHandlerMapping() {
        parsePackage.put("/test/", "");
        RequestMappingHandlerMapping mapping = applicationContext.getBean(RequestMappingHandlerMapping.class);
        // ??????url??????????????????????????????
        Map<RequestMappingInfo, HandlerMethod> map = mapping.getHandlerMethods();
        map.forEach((k, v) -> {
            PatternsRequestCondition condition = k.getPatternsCondition();
            Object[] array = condition.getPatterns().toArray();
            int length = array.length;
            boolean filter;
            for (Map.Entry<String, String> obj : parsePackage.entrySet()) {
                filter = false;
                // ???????????????
                for (int i = 0; i < length; i++) {
                    if (StringUtil.startsWith((String) array[i], obj.getKey())) {
                        Object bean = applicationContext.getBean(v.getBeanType());
                        InvocableHandlerMethod invocableHandlerMethod = new InvocableHandlerMethod(bean, v.getMethod());
                        invocableHandlerMethod.setHandlerMethodArgumentResolvers(resolvers);
                        handlers.putIfAbsent((String) array[i], invocableHandlerMethod);
                        filter = true;
                        break;
                    }
                }
                if (filter) {
                    break;
                }
            }

        });
    }

}