package de.samply.manager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class WebController {

    @RequestMapping(value = "/{path:[^\\.]*}")
    public String spa() {
        return "forward:/index.html";
    }

    @RequestMapping(value = "/{path1:[^\\.]*}/{path2:[^\\.]*}")
    public String spaLevel2() {
        return "forward:/index.html";
    }

    @RequestMapping(value = "/{path1:[^\\.]*}/{path2:[^\\.]*}/{path3:[^\\.]*}")
    public String spaLevel3() {
        return "forward:/index.html";
    }

    @RequestMapping(value = "/{path1:[^\\.]*}/{path2:[^\\.]*}/{path3:[^\\.]*}/{path4:[^\\.]*}")
    public String spaLevel4() {
        return "forward:/index.html";
    }
}
