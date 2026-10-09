package az.tapstamp.shared.persistence.mybatis;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import az.tapstamp.shared.i18n.LocalizedText;
import az.tapstamp.shared.persistence.LocalizedTextConverter;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * {@link LocalizedText} ↔ {@code jsonb} ({@code {"az": "...", "en": "...", "ru": "..."}}). Parametr mətn kimi ötürülür;
 * SQL-də {@code CAST(#{name} AS jsonb)} yazılır.
 */
public class LocalizedTextTypeHandler extends BaseTypeHandler<LocalizedText> {

    private static final LocalizedTextConverter JSON = new LocalizedTextConverter();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalizedText parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, JSON.convertToDatabaseColumn(parameter), Types.VARCHAR);
    }

    @Override
    public LocalizedText getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return JSON.convertToEntityAttribute(rs.getString(columnName));
    }

    @Override
    public LocalizedText getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return JSON.convertToEntityAttribute(rs.getString(columnIndex));
    }

    @Override
    public LocalizedText getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return JSON.convertToEntityAttribute(cs.getString(columnIndex));
    }
}
