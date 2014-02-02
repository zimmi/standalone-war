package com.rmnsc.web;

import com.rmnsc.domain.TodoItem;
import com.rmnsc.service.TodoService;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 *
 * @author thomas
 */
@Controller
@RequestMapping("/")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = Objects.requireNonNull(todoService);
    }

    @RequestMapping(method = RequestMethod.GET)
    public String home(Model model) {
        model.addAttribute("todos", todoService.getAllToDos());
        return "todo";
    }

    /**
     * Do some Post/Redirect/Get
     *
     * @param description
     * @param redirectAttributes
     * @return
     */
    @RequestMapping(method = RequestMethod.POST)
    public String store(
            @RequestParam("description") String description,
            RedirectAttributes redirectAttributes) {
        this.todoService.store(new TodoItem(description));
        redirectAttributes.addFlashAttribute("message", "Added: " + description);
        return "redirect:/";
    }
}
