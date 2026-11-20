package dev.mickablondo.cartable.course.internal.service;

import java.util.List;
import java.util.UUID;

import dev.mickablondo.cartable.course.CoursePageReview;
import dev.mickablondo.cartable.course.CourseReviewException;
import dev.mickablondo.cartable.course.CourseReviewService;
import dev.mickablondo.cartable.course.CourseStatus;
import dev.mickablondo.cartable.course.CourseTextValidated;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class CourseReviewServiceImpl implements CourseReviewService {

	private final JdbcTemplate jdbcTemplate;
	private final TransactionTemplate transactionTemplate;
	private final ApplicationEventPublisher events;

	CourseReviewServiceImpl(
			JdbcTemplate jdbcTemplate,
			TransactionTemplate transactionTemplate,
			ApplicationEventPublisher events) {
		this.jdbcTemplate = jdbcTemplate;
		this.transactionTemplate = transactionTemplate;
		this.events = events;
	}

	@Override
	public List<CoursePageReview> findPages(UUID courseId) {
		requireReviewStatus(courseId, false);
		List<CoursePageReview> pages = queryPages(courseId);
		if (pages.isEmpty()) {
			throw new CourseReviewException("Aucune page n'est disponible pour ce cours.");
		}
		return pages;
	}

	@Override
	public void updatePageText(UUID courseId, int pageNumber, String text) {
		if (text == null) {
			throw new CourseReviewException("Le texte de la page est obligatoire.");
		}
		transactionTemplate.executeWithoutResult(status -> {
			requireReviewStatus(courseId, true);
			int updatedRows = jdbcTemplate.update("""
				UPDATE course_page
				SET extracted_text = ?
				WHERE course_id = ? AND page_number = ?
				""", text, courseId, pageNumber);
			if (updatedRows != 1) {
				throw new CourseReviewException("Cette page n'existe pas pour ce cours.");
			}
			updateCourseText(courseId);
		});
	}

	@Override
	public void validate(UUID courseId) {
		transactionTemplate.executeWithoutResult(status -> {
			requireReviewStatus(courseId, true);
			int pageCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM course_page WHERE course_id = ?", Integer.class, courseId);
			if (pageCount == 0) {
				throw new CourseReviewException("Aucune page n'est disponible pour ce cours.");
			}
			int pagesWithoutText = jdbcTemplate.queryForObject("""
				SELECT COUNT(*) FROM course_page
				WHERE course_id = ? AND extracted_text IS NULL
				""", Integer.class, courseId);
			if (pagesWithoutText != 0) {
				throw new CourseReviewException("Toutes les pages doivent avoir été lues avant validation.");
			}
			String extractedText = buildCourseText(courseId);
			int updatedRows = jdbcTemplate.update("""
				UPDATE course
				SET extracted_text = ?, status = ?, text_reviewed_and_validated = TRUE
				WHERE id = ? AND status = ?
				""",
				extractedText,
				CourseStatus.VALIDATED.name(),
				courseId,
				CourseStatus.TEXT_TO_REVIEW.name());
			if (updatedRows != 1) {
				throw new CourseReviewException("Le cours n'est plus en attente de relecture.");
			}
			events.publishEvent(new CourseTextValidated(courseId));
		});
	}

	private void updateCourseText(UUID courseId) {
		int updatedRows = jdbcTemplate.update("""
			UPDATE course
			SET extracted_text = ?
			WHERE id = ? AND status = ?
			""",
			buildCourseText(courseId),
			courseId,
			CourseStatus.TEXT_TO_REVIEW.name());
		if (updatedRows != 1) {
			throw new CourseReviewException("Le cours n'est plus en attente de relecture.");
		}
	}

	private String buildCourseText(UUID courseId) {
		return jdbcTemplate.query("""
			SELECT page_number, extracted_text
			FROM course_page
			WHERE course_id = ?
			ORDER BY page_number
			""",
			(ResultSetExtractor<String>) resultSet -> {
				StringBuilder text = new StringBuilder();
				while (resultSet.next()) {
					text.append("--- Page ")
						.append(resultSet.getInt("page_number"))
						.append(" ---\n")
						.append(resultSet.getString("extracted_text"))
						.append("\n\n");
				}
				return text.toString().stripTrailing();
			},
			courseId);
	}

	private List<CoursePageReview> queryPages(UUID courseId) {
		return jdbcTemplate.query("""
			SELECT page_number, file_name, extracted_text
			FROM course_page
			WHERE course_id = ?
			ORDER BY page_number
			""",
			(RowMapper<CoursePageReview>) (resultSet, rowNumber) ->
				new CoursePageReview(
					resultSet.getInt("page_number"),
					resultSet.getString("file_name"),
					resultSet.getString("extracted_text")),
			courseId);
	}

	private void requireReviewStatus(UUID courseId, boolean lockForUpdate) {
		String query = lockForUpdate
			? "SELECT status FROM course WHERE id = ? FOR UPDATE"
			: "SELECT status FROM course WHERE id = ?";
		String status;
		try {
			status = jdbcTemplate.queryForObject(query, String.class, courseId);
		}
		catch (EmptyResultDataAccessException exception) {
			throw new CourseReviewException("Aucun cours ne correspond à cet identifiant.", exception);
		}
		if (!CourseStatus.TEXT_TO_REVIEW.name().equals(status)) {
			throw new CourseReviewException("Ce cours n'est pas en attente de relecture.");
		}
	}
}
