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
			SELECT c.id, c.file_name, c.subject, c.imported_at, c.status, c.requires_image_reading,
				c.failure_message,
				(SELECT COUNT(*) FROM course_page p WHERE p.course_id = c.id) AS total_pages,
				(SELECT COUNT(*) FROM course_page p
					WHERE p.course_id = c.id AND p.extracted_text IS NOT NULL) AS processed_pages
			FROM course c
			ORDER BY c.imported_at DESC, c.id
			""",
			(resultSet, rowNumber) -> new CourseSummary(
				resultSet.getObject("id", UUID.class),
				resultSet.getString("file_name"),
				Subject.valueOf(resultSet.getString("subject")),
				resultSet.getTimestamp("imported_at").toInstant(),
				CourseStatus.valueOf(resultSet.getString("status")),
				resultSet.getBoolean("requires_image_reading"),
				resultSet.getInt("total_pages"),
				resultSet.getInt("processed_pages"),
				resultSet.getString("failure_message")));
	}

}
