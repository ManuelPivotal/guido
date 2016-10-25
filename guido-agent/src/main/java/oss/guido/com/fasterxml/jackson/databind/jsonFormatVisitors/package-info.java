/**
 * Classes used for exposing logical structure of POJOs as Jackson
 * sees it, and exposed via
 * {@link oss.guido.com.fasterxml.jackson.databind.ObjectMapper#acceptJsonFormatVisitor(Class, JsonFormatVisitorWrapper)}
 * and
 * {@link oss.guido.com.fasterxml.jackson.databind.ObjectMapper#acceptJsonFormatVisitor(oss.guido.com.fasterxml.jackson.databind.JavaType, JsonFormatVisitorWrapper)}
 * methods.
 *<p>
 * The main entrypoint for code, then, is {@link oss.guido.com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper} and other
 * types are recursively needed during traversal.
 */
package oss.guido.com.fasterxml.jackson.databind.jsonFormatVisitors;
