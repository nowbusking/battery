/**
 * Copyright (c) 2014-2015 Spoqa, All Rights Reserved.
 */

package com.spoqa.battery;

import com.spoqa.battery.annotations.Response;
import com.spoqa.battery.codecs.JsonCodec;
import com.spoqa.battery.exceptions.DeserializationException;
import com.spoqa.battery.exceptions.IncompatibleTypeException;
import com.spoqa.battery.exceptions.MissingFieldException;
import com.spoqa.battery.exceptions.RpcException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public final class ObjectBuilder {
    private static final String TAG = "ObjectBuilder";

    private static Map<String, ResponseDeserializer> sDeserializerMap;

    static {
        sDeserializerMap = new HashMap<String, ResponseDeserializer>();
        registerDeserializer(new JsonCodec());
    }

    public static void registerDeserializer(ResponseDeserializer deserializer) {
        try {
            sDeserializerMap.put(deserializer.deserializationContentType(), deserializer);
        } catch (Exception e) {
            Logger.error(TAG, "Could not register deserializer class");
            e.printStackTrace();
        }
    }

    public static void build(String contentType, String input, Object object,
                             FieldNameTranslator translator, TypeAdapterCollection typeAdapters)
            throws DeserializationException {
        String mime = extractMime(contentType);

        if (Config.DEBUG_DUMP_RESPONSE) {
            Logger.debug(TAG, "Mime: " + mime);
            Logger.debug(TAG, "Response: " + input);
        }
        
        if (!sDeserializerMap.containsKey(mime)) {
            RpcException e = new RpcException(String.format("No deserializer associated with MIME type %1$s", mime));
            throw new DeserializationException(e);
        }

        ReflectionCache cache = new ReflectionCache();

        try {
            boolean filterByAnnotation = false;
            CodecUtils.ResponseWithTypeParameters rt = CodecUtils.getResponseObject(cache, object, true);
            Object responseObject = rt.object;
            if (responseObject == null) {
                responseObject = object;
                filterByAnnotation = true;
            }
            deserializeObject(cache, sDeserializerMap.get(mime), input, responseObject,
                    translator, typeAdapters, filterByAnnotation, rt.typeVariables);
        } catch (RpcException e) {
            throw new DeserializationException(e);
        }
    }

    private static String extractMime(String contentType) {
        String[] parts = contentType.split(";");
        if (parts.length == 0)
            return null;
        return parts[0].trim();
    }

    private static void deserializeObject(ReflectionCache cache, ResponseDeserializer deserializer,
                                          String input, Object object, FieldNameTranslator translator,
                                          TypeAdapterCollection typeAdapters, boolean filterByAnnotation,
                                          Type[] genericTypes)
            throws DeserializationException {
        /* Let's assume the root element is always an object */
        Object internalObject = deserializer.parseInput(input);

        visitObject(cache, deserializer, internalObject, object,
                translator, typeAdapters, filterByAnnotation, genericTypes);
    }

    private static void visitObject(ReflectionCache cache, ResponseDeserializer deserializer,
                                    Object internalObject, Object dest, FieldNameTranslator translator,
                                    TypeAdapterCollection typeAdapters,
                                    boolean filterByAnnotation, Type[] genericTypes)
            throws DeserializationException {
        List<Field> fields;
        List<Method> setters;
        Class clazz = dest.getClass();

        if (filterByAnnotation) {
            fields = CodecUtils.getAnnotatedFields(cache, Response.class, clazz);
            setters = CodecUtils.getAnnotatedSetterMethods(cache, Response.class, clazz);
        } else {
            fields = CodecUtils.getAllFields(cache, clazz);
            setters = CodecUtils.getAllSetterMethods(cache, clazz);
        }

        TypeVariable tvs[] = clazz.getTypeParameters();
        try {
            /* search fields */
            for (Field f : fields) {
                String fieldName = f.getName();
                String docName = null;
                boolean explicit = false;
                boolean hasValue = false;
                Class fieldType = f.getType();

                Type genericType = f.getGenericType();
                if (genericType instanceof TypeVariable && genericTypes != null &&
                        genericTypes.length > 0 && tvs.length > 0) {
                    for (int i = 0; i < tvs.length; ++i) {
                        if (genericType.equals(tvs[i])) {
                            if (genericTypes[i] instanceof ParameterizedType) {
                                ParameterizedType inner = (ParameterizedType) genericTypes[i];
                                Type[] innerTypeArgs = inner.getActualTypeArguments();
                                Type[] ts = new Type[tvs.length + innerTypeArgs.length];
                                for (int j = 0; j < genericTypes.length; ++j)
                                    ts[j] = genericTypes[j];
                                for (int j = 0; j < innerTypeArgs.length; ++j)
                                    ts[j + genericTypes.length] = innerTypeArgs[j];
                                genericTypes = ts;
                                fieldType = (Class) inner.getRawType();
                            } else {
                                fieldType = (Class) genericTypes[i];
                            }
                            break;
                        }
                    }
                }

                if (Config.DEBUG_DUMP_RESPONSE) {
                    Logger.debug(TAG, "read field " + fieldName);
                }

                Response annotation;
                if (cache.containsFieldAnnotation(f, Response.class)) {
                    annotation = (Response) cache.queryFieldAnnotation(f, Response.class);
                } else {
                    annotation = f.getAnnotation(Response.class);
                    cache.cacheFieldAnnotation(f, Response.class, annotation);
                }

                if (annotation != null) {
                    if (annotation.value().length() > 0) {
                        docName = annotation.value();
                        explicit = true;
                    }
                }

                if (docName == null)
                    docName = translator.localToRemote(fieldName);

                /* check for field names */
                Object value = null;
                if (explicit && docName.contains(".")) {
                    try {
                        value = findChild(deserializer, internalObject, docName);
                        hasValue = true;
                    } catch (NoSuchElementException e) {
                        value = null;
                    }
                } else if (internalObject != null) {
                    value = deserializer.queryObjectChild(internalObject, docName);
                    hasValue = deserializer.containsChild(internalObject, docName);
                }

                if (!explicit && !hasValue) {
                    /* fall back to the untransformed name */
                    if (deserializer.containsChild(internalObject, fieldName)) {
                        value = deserializer.queryObjectChild(internalObject, fieldName);
                        hasValue = true;
                    }
                }

                if (annotation != null && annotation.required() && !hasValue) {
                    /* check for mandatory field */
                    throw new DeserializationException(new MissingFieldException(f.getName()));
                }

                if (hasValue) {
                    if (internalObject == null || value == null) {
                        f.set(dest, null);
                    } else if (typeAdapters.contains(fieldType) &&
                            CodecUtils.isBuiltIn(value.getClass())) {
                        TypeAdapter codec = typeAdapters.query(fieldType);
                        f.set(dest, codec.decode(value.toString()));
                    } else if (CodecUtils.isString(fieldType)) {
                        f.set(dest, value.toString());
                    } else if (CodecUtils.isIntegerPrimitive(fieldType)) {
                        f.setInt(dest, CodecUtils.parseInteger(fieldName, value));
                    } else if (CodecUtils.isIntegerBoxed(fieldType)) {
                        f.set(dest, CodecUtils.parseInteger(fieldName, value));
                    } else if (CodecUtils.isLongPrimitive(fieldType)) {
                        f.setLong(dest, CodecUtils.parseLong(fieldName, value));
                    } else if (CodecUtils.isLongBoxed(fieldType)) {
                        f.set(dest, CodecUtils.parseLong(fieldName, value));
                    } else if (CodecUtils.isList(fieldType)) {
                        if (fieldType != List.class && fieldType != ArrayList.class) {
                            Logger.error(TAG, String.format("field '%1$s' is not ArrayList or its superclass.",
                                    fieldName));
                            continue;
                        }
                        if (!deserializer.isArray(value.getClass())) {
                            Logger.error(TAG, String.format("internal class of '%1$s' is not an array",
                                    fieldName));
                            continue;
                        }
                        List newList = ArrayList.class.newInstance();
                        visitArray(cache, deserializer, value, newList,
                                CodecUtils.getGenericTypeOfField(dest.getClass(), f.getName(), genericTypes),
                                translator, typeAdapters);
                        f.set(dest, newList);
                    } else if (CodecUtils.isMap(fieldType)) {
                        Map newMap = (Map) fieldType.newInstance();
                        visitMap(cache, deserializer, value, newMap);
                        f.set(dest, newMap);
                    } else if (CodecUtils.isBooleanPrimitive(fieldType)) {
                        f.setBoolean(dest, CodecUtils.parseBoolean(fieldName, value));
                    } else if (CodecUtils.isBooleanBoxed(fieldType)) {
                        f.set(dest, CodecUtils.parseBoolean(fieldName, value));
                    } else if (CodecUtils.isFloatPrimitive(fieldType)) {
                        f.setFloat(dest, CodecUtils.parseFloat(fieldName, value));
                    } else if (CodecUtils.isFloatBoxed(fieldType)) {
                        f.set(dest, CodecUtils.parseFloat(fieldName, value));
                    } else if (CodecUtils.isDoublePrimitive(fieldType)) {
                        f.setDouble(dest, CodecUtils.parseDouble(fieldName, value));
                    } else if (CodecUtils.isDoubleBoxed(fieldType)) {
                        f.set(dest, CodecUtils.parseDouble(fieldName, value));
                    } else if (fieldType.isEnum()) {
                        f.set(dest, CodecUtils.parseEnum(fieldType, value.toString()));
                    } else {
                        if (!CodecUtils.shouldBeExcluded(fieldType)) {
                            /* or it should be a POJO... */
                            Type[] typeArguments = null;
                            if (genericType instanceof ParameterizedType) {
                                ParameterizedType pt = (ParameterizedType) genericType;
                                typeArguments = pt.getActualTypeArguments();
                            }

                            Object newObject = fieldType.newInstance();
                            visitObject(cache, deserializer, value, newObject, translator,
                                    typeAdapters, false, typeArguments);
                            f.set(dest, newObject);
                        }
                    }
                } else {
                    if (!CodecUtils.isPrimitive(fieldType))
                        f.set(dest, null);
                }
            }

            /* search methods */
            for (Method m : setters) {
                String fieldName = CodecUtils.normalizeSetterName(m.getName());
                String docName = null;
                boolean explicit = false;
                boolean hasValue = false;
                Class fieldType = m.getParameterTypes()[0];

                Type genericType = m.getGenericParameterTypes()[0];
                if (genericType instanceof TypeVariable && genericTypes != null &&
                        genericTypes.length > 0 && tvs.length > 0) {
                    for (int i = 0; i < tvs.length; ++i) {
                        if (genericType.equals(tvs[i])) {
                            fieldType = (Class) genericTypes[i];
                            break;
                        }
                    }
                }

                if (Config.DEBUG_DUMP_RESPONSE) {
                    Logger.debug(TAG, "read method " + fieldName);
                }

                Response annotation;
                if (cache.containsMethodAnnotation(m, Response.class)) {
                    annotation = (Response) cache.queryMethodAnnotation(m, Response.class);
                } else {
                    annotation = m.getAnnotation(Response.class);
                    cache.cacheMethodAnnotation(m, Response.class, annotation);
                }

                if (annotation != null) {
                    if (annotation.value().length() > 0) {
                        docName = annotation.value();
                        explicit = true;
                    }
                }

                if (docName == null)
                    docName = translator.localToRemote(fieldName);

                /* check for field names */
                Object value = null;
                if (explicit && docName.contains(".")) {
                    try {
                        value = findChild(deserializer, internalObject, docName);
                        hasValue = true;
                    } catch (NoSuchElementException e) {
                        value = null;
                    }
                } else if (internalObject != null) {
                    value = deserializer.queryObjectChild(internalObject, docName);
                    hasValue = deserializer.containsChild(internalObject, docName);
                }

                if (!explicit && !hasValue) {
                    /* fall back to the untransformed name */
                    if (deserializer.containsChild(internalObject, fieldName)) {
                        value = deserializer.queryObjectChild(internalObject, fieldName);
                        hasValue = true;
                    }
                }

                if (annotation != null && annotation.required() && !hasValue) {
                    /* check for mandatory field */
                    throw new DeserializationException(new MissingFieldException(fieldName));
                }

                if (hasValue) {
                    if (internalObject == null || value == null) {
                        //m.invoke(dest, null);
                    } else if (typeAdapters.contains(fieldType) &&
                            CodecUtils.isBuiltIn(value.getClass())) {
                        TypeAdapter codec = typeAdapters.query(fieldType);
                        m.invoke(dest, codec.decode(value.toString()));
                    } else if (CodecUtils.isString(fieldType)) {
                        m.invoke(dest, value.toString());
                    } else if (CodecUtils.isInteger(fieldType)) {
                        m.invoke(dest, CodecUtils.parseInteger(fieldName, value));
                    } else if (CodecUtils.isLong(fieldType)) {
                        m.invoke(dest, CodecUtils.parseLong(fieldName, value));
                    } else if (CodecUtils.isList(fieldType)) {
                        if (fieldType != List.class && fieldType != ArrayList.class) {
                            Logger.error(TAG, String.format("argument of method '%1$s' is not " +
                                    "ArrayList or its superclass.",
                                    fieldName));
                            continue;
                        }
                        if (!deserializer.isArray(value.getClass())) {
                            Logger.error(TAG, String.format("internal class of '%1$s' is not an array",
                                    fieldName));
                            continue;
                        }
                        List newList = ArrayList.class.newInstance();
                        visitArray(cache, deserializer, value, newList,
                                CodecUtils.getGenericTypeOfMethod(dest.getClass(), m.getName(), List.class),
                                translator, typeAdapters);
                        m.invoke(dest, newList);
                    } else if (CodecUtils.isMap(fieldType)) {
                        Map newMap = (Map) fieldType.newInstance();
                        visitMap(cache, deserializer, value, newMap);
                        m.invoke(dest, newMap);
                    } else if (CodecUtils.isBoolean(fieldType)) {
                        m.invoke(dest, CodecUtils.parseBoolean(fieldName, value));
                    } else if (CodecUtils.isFloat(fieldType)) {
                        m.invoke(dest, CodecUtils.parseFloat(fieldName, value));
                    } else if (CodecUtils.isDouble(fieldType)) {
                        m.invoke(dest, CodecUtils.parseDouble(fieldName, value));
                    } else if (fieldType.isEnum()) {
                        m.invoke(dest, CodecUtils.parseEnum(fieldType, value.toString()));
                    } else {
                        if (!CodecUtils.shouldBeExcluded(fieldType)) {
                            /* or it should be a POJO... */
                            Type[] typeArguments = null;
                            if (genericType instanceof ParameterizedType) {
                                ParameterizedType pt = (ParameterizedType) genericType;
                                typeArguments = pt.getActualTypeArguments();
                            }

                            Object newObject = fieldType.newInstance();
                            visitObject(cache, deserializer, value, newObject, translator,
                                    typeAdapters, false, typeArguments);
                            m.invoke(dest, newObject);
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (BuildConfig.DEBUG)
                e.printStackTrace();
            throw new DeserializationException(e);
        } catch (IncompatibleTypeException e) {
            if (BuildConfig.DEBUG)
                e.printStackTrace();
            throw new DeserializationException(e);
        }
    }

    private static void visitArray(ReflectionCache cache,
                                   ResponseDeserializer deserializer, Object internalArray,
                                   List<?> output, Class innerType,
                                   FieldNameTranslator translator,
                                   TypeAdapterCollection typeAdapters) throws DeserializationException {
        try {
            Method add = List.class.getDeclaredMethod("add", Object.class);
            Integer index = 0;

            for (Object element : deserializer.queryArrayChildren(internalArray)) {
                if (element == null) {
                    add.invoke(output, (Object) null);
                } else if (CodecUtils.isList(innerType)) {
                    /* TODO implement nested list */
                } else if (CodecUtils.isMap(innerType)) {
                    /* TODO implement nested map */
                } else if (deserializer.isObject(element.getClass())) {
                    Object o = innerType.newInstance();
                    visitObject(cache, deserializer, element, o, translator, typeAdapters, false, null);
                    add.invoke(output, o);
                } else {
                    Object newElem = element;
                    try {
                        if (typeAdapters.contains(innerType))
                            newElem = typeAdapters.query(innerType).decode(element.toString());
                        else if (CodecUtils.isString(innerType))
                            newElem = CodecUtils.parseString(element);
                        else if (CodecUtils.isInteger(innerType))
                            newElem = CodecUtils.parseInteger(index.toString(), element);
                        else if (CodecUtils.isBoolean(innerType))
                            newElem = CodecUtils.parseBoolean(index.toString(), element);
                        else if (CodecUtils.isDouble(innerType))
                            newElem = CodecUtils.parseDouble(index.toString(), element);
                        else if (CodecUtils.isFloat(innerType))
                            newElem = CodecUtils.parseFloat(index.toString(), element);
                        else if (CodecUtils.isLong(innerType))
                            newElem = CodecUtils.parseLong(index.toString(), element);
                    } catch (IncompatibleTypeException e) {
                        throw new DeserializationException(e);
                    }

                    add.invoke(output, newElem);

                    ++index;
                }
            }
        } catch (NoSuchMethodException e) {
            // there's no List without add()
        } catch (InvocationTargetException e) {
            throw new DeserializationException(e);
        } catch (IllegalAccessException e) {
            throw new DeserializationException(e);
        } catch (InstantiationException e) {
            throw new DeserializationException(e);
        }
    }

    private static void visitMap(ReflectionCache cache, ResponseDeserializer deserializer,
                                 Object internalObject, Map<?, ?> m) throws DeserializationException {
        /* TODO implement deserialization into Map<?,?> */
    }

    private static Object findChild(ResponseDeserializer deserializer, Object internalObject,
                             String path) throws NoSuchElementException {
        String[] frags = path.split("\\.");

        // Return one child if the key's not meant to be a path
        if (deserializer.containsChild(internalObject, path))
            return deserializer.queryObjectChild(internalObject, path);

        int i = 0;
        for (String frag : frags) {
            if (++i == frags.length) {
                // if the last object
                if (!deserializer.containsChild(internalObject, frag))
                    throw new NoSuchElementException();
                return deserializer.queryObjectChild(internalObject, frag);
            } else {
                internalObject = deserializer.queryObjectChild(internalObject, frag);
                if (internalObject == null)
                    break;
            }
        }

        throw new NoSuchElementException();
    }

}
