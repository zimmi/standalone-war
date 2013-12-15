package com.rmnsc.persistence;

import com.rmnsc.domain.TodoItem;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 *
 * @author thomas
 */
@Transactional(propagation = Propagation.MANDATORY)
public class TodoDaoJdbc extends AbstractJdbcTemplateDao implements TodoDao {

    @AutowireSql
    private String dml_insert_todo_item;
    @AutowireSql
    private String dml_query_todo_item_all;

    public TodoDaoJdbc(NamedParameterJdbcOperations jdbcOperations) {
        super(jdbcOperations);
    }

    @Override
    public void store(TodoItem item) {
        jdbcOperations.update(
                dml_insert_todo_item,
                new MapSqlParameterSource(
                "description", item.getDescription()));
    }

    @Override
    public Iterable<TodoItem> getAll() {
        return jdbcOperations.query(dml_query_todo_item_all, TODO_MAPPER);
    }

    @Override
    public TodoItem getById(int id) {
        return jdbcOperations.queryForObject(dml_insert_todo_item, new MapSqlParameterSource("todo_item_id", id), TODO_MAPPER);
    }
    private static final RowMapper<TodoItem> TODO_MAPPER = new RowMapper<TodoItem>() {
        @Override
        public TodoItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new TodoItem(rs.getString("description"));
        }
    };
}