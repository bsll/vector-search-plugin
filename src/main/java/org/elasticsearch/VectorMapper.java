package org.elasticsearch;

import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.support.XContentMapValues;
import org.elasticsearch.index.mapper.*;
import org.elasticsearch.index.query.QueryShardContext;
import org.elasticsearch.index.query.QueryShardException;

import java.io.IOException;
import java.util.List;
import java.util.Map;


public class VectorMapper extends FieldMapper {

    public static final String CONTENT_TYPE = "vector";
    public static final String DIMENSIONS = "dimensions";

    public static final int DEFAULT_DIMENSIONS = 8;

    public static class Defaults {
        public static final MappedFieldType FIELD_TYPE = new VectorSearchFieldType();
        static {
            FIELD_TYPE.setTokenized(false);
            FIELD_TYPE.setHasDocValues(false);
            FIELD_TYPE.setDimensions(DEFAULT_DIMENSIONS, 8);
            FIELD_TYPE.setIndexOptions(IndexOptions.DOCS);
            FIELD_TYPE.freeze();
        }
    }

    public static class Builder extends FieldMapper.Builder<Builder, VectorMapper> {
        public Builder(String name) {
            super(name, Defaults.FIELD_TYPE, Defaults.FIELD_TYPE);
            builder = this;
        }

        @Override
        public VectorMapper build(BuilderContext context) {
            setupFieldType(context);
            return new VectorMapper(name, fieldType, defaultFieldType, context.indexSettings(), multiFieldsBuilder.build(this, context), copyTo);
        }

        @Override
        protected void setupFieldType(BuilderContext context) {
            this.fieldType.setName(this.buildFullName(context));
        }
    }

    public static class TypeParser implements Mapper.TypeParser {
        @Override
        public Mapper.Builder<?, ?> parse(String name, Map<String, Object> node, ParserContext parserContext) throws MapperParsingException {
            Builder builder = new Builder(name);
            if (node.containsKey(DIMENSIONS)) {
                builder.fieldType().setDimensions(XContentMapValues.nodeIntegerValue(node.get(DIMENSIONS), 8), Double.BYTES);
                node.remove(DIMENSIONS);
            }
            //TypeParsers.parseField(builder, name, node, parserContext);
            return builder;
        }
    }

    @Override
    protected void doXContentBody(XContentBuilder builder, boolean includeDefaults, Params params) throws IOException {
        super.doXContentBody(builder, includeDefaults, params);

        if (includeDefaults || fieldType().pointDimensionCount() != DEFAULT_DIMENSIONS) {
            builder.field(DIMENSIONS, fieldType().pointDimensionCount());
        }
    }

    public static class VectorSearchFieldType extends SimpleMappedFieldType {

        public VectorSearchFieldType() {
        }

        protected VectorSearchFieldType(VectorSearchFieldType ref) {
            super(ref);
        }

        @Override
        public String typeName() {
            return CONTENT_TYPE;
        }

        @Override
        public VectorSearchFieldType clone() {
            return new VectorSearchFieldType(this);
        }

        @Override
        public Query existsQuery(QueryShardContext context) {
            return new TermQuery(new Term("_field_names", this.name()));
        }

        @Override
        public Query termQuery(Object value, QueryShardContext context) {
            throw new QueryShardException(context, "No exact searching on this field! [" + name() + "]");
        }

        @Override
        public Query rangeQuery(Object lowerTerm, Object upperTerm, boolean includeLower, boolean includeUpper, QueryShardContext context) {
            double[] l = new double[] { Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                                        Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY };

            double[] u = new double[] { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                                        Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY };

            if (lowerTerm != null) {
                l = parseDoublesInString(((BytesRef)lowerTerm).utf8ToString());
                if (l.length != pointDimensionCount())
                    throw new ElasticsearchParseException("Data has wrong number of dimensions %d (should be %d)", l.length, pointDimensionCount());
            }

            if (upperTerm != null) {
                u = parseDoublesInString(((BytesRef)upperTerm).utf8ToString());
                if (u.length != pointDimensionCount())
                    throw new ElasticsearchParseException("Data has wrong number of dimensions %d (should be %d)", u.length, pointDimensionCount());
            }

            return DoublePoint.newRangeQuery(this.name(), l, u);
        }
    }

    protected VectorMapper(String simpleName, MappedFieldType fieldType, MappedFieldType defaultFieldType, Settings indexSettings, MultiFields multiFields, CopyTo copyTo) {
        super(simpleName, fieldType, defaultFieldType, indexSettings, multiFields, copyTo);
    }

    @Override
    protected String contentType() {
        return CONTENT_TYPE;
    }

    @Override
    protected void parseCreateField(ParseContext context, List<IndexableField> fields) throws IOException {
        final String value = context.parser().textOrNull();

        if (value != null) {
            double[] values = parseDoublesInString(value);
            if (values.length != fieldType().pointDimensionCount())
                throw new ElasticsearchParseException("Data has wrong number of dimensions %d (should be %d)", values.length, fieldType().pointDimensionCount());
            fields.add(new DoublePoint(fieldType().name(), values));
        }
    }

    private static double[] parseDoublesInString(String data) {
        String[] parts = data.split(",");

        double[] res = new double[parts.length];
        for (int i = 0; i < res.length; ++i) {
            res[i] = Double.parseDouble(parts[i]);
        }

        return res;
    }

}