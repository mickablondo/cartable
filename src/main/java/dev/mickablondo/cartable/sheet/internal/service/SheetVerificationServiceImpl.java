package dev.mickablondo.cartable.sheet.internal.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.mickablondo.cartable.course.Subject;
import dev.mickablondo.cartable.sheet.SheetStatus;
import dev.mickablondo.cartable.sheet.SheetVerificationService;
import dev.mickablondo.cartable.sheet.SheetVerified;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SheetVerificationServiceImpl implements SheetVerificationService {

	private final JdbcTemplate jdbcTemplate;
	private final ApplicationEventPublisher events;

	SheetVerificationServiceImpl(JdbcTemplate jdbcTemplate, ApplicationEventPublisher events) {
		this.jdbcTemplate = jdbcTemplate;
		this.events = events;
	}

	@Override
	@Transactional
	public void verify(UUID sheetId) {
		if (sheetId == null) {
			throw new IllegalArgumentException("L'identifiant de la fiche est obligatoire.");
		}

		List<SheetIdentity> sheets = jdbcTemplate.query("""
			SELECT subject, theme, status
			FROM sheet
			WHERE id = ?
			FOR UPDATE
			""",
			(resultSet, rowNumber) -> new SheetIdentity(
				Subject.valueOf(resultSet.getString("subject")),
				resultSet.getString("theme"),
				SheetStatus.valueOf(resultSet.getString("status"))),
			sheetId);
		if (sheets.isEmpty()) {
			throw new IllegalArgumentException("Aucune fiche ne correspond à cet identifiant.");
		}

		SheetIdentity sheet = sheets.getFirst();
		if (sheet.status() != SheetStatus.TO_VERIFY) {
			throw new IllegalStateException("Cette fiche a déjà été validée.");
		}

		int updatedRows = jdbcTemplate.update(
			"UPDATE sheet SET status = ? WHERE id = ? AND status = ?",
			SheetStatus.VERIFIED.name(),
			sheetId,
			SheetStatus.TO_VERIFY.name());
		if (updatedRows != 1) {
			throw new IllegalStateException("Le statut de la fiche a changé pendant sa validation.");
		}

		events.publishEvent(new SheetVerified(sheetId, sheet.subject(), sheet.theme(), Instant.now()));
	}

	private record SheetIdentity(Subject subject, String theme, SheetStatus status) {
	}

}
