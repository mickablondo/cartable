package dev.mickablondo.cartable.course.internal.service;

import java.util.List;
import java.util.UUID;

import dev.mickablondo.cartable.course.CourseQueryService;
import dev.mickablondo.cartable.course.CourseSummary;
import dev.mickablondo.cartable.course.CourseStatus;
import dev.mickablondo.cartable.course.Subject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class CourseQueryServiceImpl implements CourseQueryService {

	private final JdbcTemplate jdbcTemplate;

	CourseQueryServiceImpl(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public List<CourseSummary> findAll() {
		return jdbcTemplate.query("""
			SELECT id, file_name, subject, imported_at, status, requires_image_reading
			FROM course
			ORDER BY imported_at DESC, id
			""",
			(resultSet, rowNumber) -> new CourseSummary(
				resultSet.getObject("id", UUID.class),
				resultSet.getString("file_name"),
				Subject.valueOf(resultSet.getString("subject")),
				resultSet.getTimestamp("imported_at").toInstant(),
				CourseStatus.valueOf(resultSet.getString("status")),
				resultSet.getBoolean("requires_image_reading")));
	}

}
