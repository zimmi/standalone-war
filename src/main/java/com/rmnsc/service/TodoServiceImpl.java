package com.rmnsc.service;

import com.rmnsc.domain.TodoItem;
import com.rmnsc.persistence.TodoDao;
import java.util.Objects;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author thomas
 */
@Transactional(propagation = Propagation.REQUIRED)
public class TodoServiceImpl implements TodoService {

    private final TodoDao todoDao;

    public TodoServiceImpl(TodoDao todoDao) {
        this.todoDao = Objects.requireNonNull(todoDao, "todoDao must not be null");
    }

    @Override
    public Iterable<TodoItem> getAllToDos() {
        return todoDao.getAll();
    }

    @Override
    public void store(TodoItem item) {
        todoDao.store(item);
    }
}