package com.rmnsc.service;

import com.rmnsc.domain.TodoItem;

/**
 *
 * @author thomas
 */
public interface TodoService {

    public Iterable<TodoItem> getAllToDos();

    public void store(TodoItem item);
}