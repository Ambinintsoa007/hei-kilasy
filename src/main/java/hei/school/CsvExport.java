package hei.school;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Grades export for the registrar's office (opened in Excel, semicolon separator).
 */
@RestController
public class CsvExport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private BigDecimal penalizedGrade(BigDecimal raw, BigDecimal max, Object deadline, Object submittedAt) {
        long d = ((java.sql.Date) deadline).toLocalDate().toEpochDay();
        long s = ((java.sql.Timestamp) submittedAt).toLocalDateTime().toLocalDate().toEpochDay();
        long days = Math.max(0, s - d);
        BigDecimal penalty = BigDecimal.valueOf(days)
                .multiply(new BigDecimal("0.10"))
                .min(new BigDecimal("0.50"));
        BigDecimal base = raw.multiply(new BigDecimal("20")).divide(max, 10, RoundingMode.HALF_UP);
        return base.multiply(BigDecimal.ONE.subtract(penalty))
                .max(BigDecimal.ZERO)
                .setScale(1, RoundingMode.HALF_UP);
    }

    @GetMapping("/exports/grades.csv")
    public ResponseEntity<String> export() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT st.reference, st.full_name, st.email, st.cohort, e.title, e.deadline, s.submitted_at, s.raw_points, e.max_points, "
                        + "(s.submitted_at::date > e.deadline) AS late "
                        + "FROM submission s JOIN student st ON st.id = s.student_id JOIN exercise e ON e.id = s.exercise_id "
                        + "ORDER BY st.full_name, e.deadline");

        StringBuilder csv = new StringBuilder();
        csv.append("reference;etudiant;email;promotion;exercice;echeance;soumis_le;en_retard;note_sur_20\n");
        for (Map<String, Object> row : rows) {
            String note = "";
            if (row.get("raw_points") != null) {
                BigDecimal raw = (BigDecimal) row.get("raw_points");
                BigDecimal max = (BigDecimal) row.get("max_points");
                BigDecimal n = penalizedGrade(raw, max, row.get("deadline"), row.get("submitted_at"));
                note = n.toPlainString().replace('.', ',');
            }
            csv.append(row.get("reference")).append(';')
                    .append(row.get("full_name")).append(';')
                    .append(row.get("email")).append(';')
                    .append(row.get("cohort")).append(';')
                    .append(row.get("title")).append(';')
                    .append(row.get("deadline")).append(';')
                    .append(row.get("submitted_at")).append(';')
                    .append(Boolean.TRUE.equals(row.get("late")) ? "oui" : "non").append(';')
                    .append(note).append('\n');
        }

        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"notes-kilasy.csv\"")
                .body(csv.toString());
    }
}
