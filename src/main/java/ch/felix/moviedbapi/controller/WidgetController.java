package ch.felix.moviedbapi.controller;

import ch.felix.moviedbapi.service.UserIndicatorService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.net.InetSocketAddress;
import java.net.Socket;

@Controller
@RequestMapping("status")
public class WidgetController {

    private UserIndicatorService userIndicatorService;

    public WidgetController(UserIndicatorService userIndicatorService) {
        this.userIndicatorService = userIndicatorService;
    }

    private String checkOnline(String ip, Integer port) {
        String status = "Online";
        try {
            InetSocketAddress sa = new InetSocketAddress(ip, port);
            Socket socket = new Socket();
            socket.connect(sa, 200);
            socket.close();
        } catch (Exception e) {
            status = "Offline";
        }
        return status;
    }

    @GetMapping
    public String getServerStatus(Model model) {
        model.addAttribute("hermann", checkOnline("scorewinner.ch", 8090));
        model.addAttribute("moviedb", checkOnline("scorewinner.ch", 8080));
        model.addAttribute("minecraft", checkOnline("scorewinner.ch", 25565));
        return "widget/status.html";
    }

    @GetMapping("user")
    public String getCurrentUser(Model model, HttpServletRequest request) {
        userIndicatorService.allowGuest(model, request);
        return "widget/user.html";
    }
}