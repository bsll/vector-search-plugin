package org.elasticsearch;

import org.elasticsearch.index.mapper.Mapper;
import org.elasticsearch.plugins.MapperPlugin;
import org.elasticsearch.plugins.Plugin;

import java.util.Collections;
import java.util.Map;

/**
 * Plugin class, this registers our custom field type with the Elasticsearch internals
 */
public class VectorSearchPlugin extends Plugin implements MapperPlugin {
    @Override
    public Map<String, Mapper.TypeParser> getMappers() {
        return Collections.singletonMap(VectorMapper.CONTENT_TYPE, new VectorMapper.TypeParser());
    }
}
