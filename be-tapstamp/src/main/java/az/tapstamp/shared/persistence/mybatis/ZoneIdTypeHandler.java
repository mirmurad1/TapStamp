package az.tapstamp.shared.persistence.mybatis;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** {@link ZoneId} ↔ IANA saat qurşağının adı (məs. {@code Asia/Baku}). */
public class ZoneIdTypeHandler extends BaseTypeHandler<ZoneId> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, ZoneId parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.getId());
    }

    @Override
    public ZoneId getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toZone(rs.getString(columnName));
    }

    @Override
    public ZoneId getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toZone(rs.getString(columnIndex));
    }

    @Override
    public ZoneId getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toZone(cs.getString(columnIndex));
    }

    private static ZoneId toZone(String id) {
        return id == null ? null : ZoneId.of(id);
    }
}
