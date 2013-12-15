
package com.rmnsc.persistence;

import com.rmnsc.domain.TodoItem;

/**
 *
 * @author thomas
 */
public interface TodoDao {

    public void store(TodoItem item);

    public Iterable<TodoItem> getAll();

    public TodoItem getById(int id);

}