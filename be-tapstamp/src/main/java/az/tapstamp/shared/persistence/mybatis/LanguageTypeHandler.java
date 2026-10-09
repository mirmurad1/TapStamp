package az.tapstamp.shared.persistence.mybatis;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import az.tapstamp.shared.i18n.Language;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** {@link Language} ↔ dil kodu ({@code az}, {@code en}, {@code ru}); naməlum kod standart dilə çevrilir. */
public class LanguageTypeHandler extends BaseTypeHandler<Language> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Language parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.tag());
    }

    @Override
    public Language getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toLanguage(rs.getString(columnName));
    }

    @Override
    public Language getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toLanguage(rs.getString(columnIndex));
    }

    @Override
    public Language getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toLanguage(cs.getString(columnIndex));
    }

    private static Language toLanguage(String tag) {
        return tag == null ? null : Language.fromTag(tag).orElse(Language.DEFAULT);
    }
}
