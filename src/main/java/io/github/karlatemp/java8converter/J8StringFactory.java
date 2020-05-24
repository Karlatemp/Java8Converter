/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/05/24 18:11:17
 *
 * Java8Converter/Java8Converter.main/J8StringFactory.java
 */

package io.github.karlatemp.java8converter;

import java.lang.invoke.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class J8StringFactory {

    /**
     * Tag used to demarcate an ordinary argument.
     */
    private static final char TAG_ARG = '\u0001';

    /**
     * Tag used to demarcate a constant.
     */
    private static final char TAG_CONST = '\u0002';
    private final int aCount;
    private final Object[] constants;
    private final char[] chars;
    public static final ConcurrentHashMap<List<Object>, MethodHandle> CACHE = new ConcurrentHashMap<>();

    public J8StringFactory(char[] chars, Object[] constants, int aCount) {
        this.chars = chars;
        this.constants = constants;
        this.aCount = aCount;
    }

    public String toString(Object... args) {
        if (aCount != args.length) {
            throw new RuntimeException(
                    "Mismatched number of concat constants: recipe wants " +
                            aCount +
                            " constants, but only " +
                            args.length +
                            " are passed");
        }
        StringBuilder sb = new StringBuilder(chars.length);
        int a = 0, c = 0;
        for (char w : chars) {
            switch (w) {
                case TAG_CONST: {
                    sb.append(constants[c++]);
                    break;
                }
                case TAG_ARG: {
                    sb.append(args[a++]);
                    break;
                }
                default: {
                    sb.append(w);
                }
            }
        }
        return sb.toString();
    }

    public static CallSite makeConcatWithConstants(
            MethodHandles.Lookup lookup,
            String name,
            MethodType concatType,
            String recipe,
            Object... constants) throws NoSuchMethodException, IllegalAccessException {
        Objects.requireNonNull(lookup, "lookup");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(concatType, "concatType");
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(constants, "constants");
        List<Object> key = Arrays.asList(recipe, concatType, Arrays.asList(constants));
        MethodHandle cache = CACHE.get(key);
        if (cache != null) return new ConstantCallSite(cache);
        J8StringFactory parse = parse(concatType, recipe, constants);
        MethodHandle mh = lookup.findVirtual(J8StringFactory.class, "toString", MethodType.methodType(String.class, Object[].class)).bindTo(parse);
        mh = mh.asVarargsCollector(Object[].class).asType(concatType);
        CACHE.put(key, mh);
        return new ConstantCallSite(mh);
    }

    private static J8StringFactory parse(MethodType concatType, String recipe, Object[] constants) {
        if (!concatType.returnType().isAssignableFrom(String.class)) {
            throw new RuntimeException(
                    "The return type should be compatible with String, but it is " +
                            concatType.returnType());
        }
        char[] chars = recipe.toCharArray();
        int cCount = 0, aCount = 0;
        for (char c : chars) {
            switch (c) {
                case TAG_CONST:
                    cCount++;
                    break;
                case TAG_ARG:
                    aCount++;
                    break;
            }
        }
        if (cCount != constants.length) {
            throw new RuntimeException(
                    "Mismatched number of concat constants: recipe wants " +
                            cCount +
                            " constants, but only " +
                            constants.length +
                            " are passed");
        }
        if (aCount != concatType.parameterCount()) {
            throw new RuntimeException(
                    "Mismatched number of concat arguments: recipe wants " +
                            aCount +
                            " arguments, but signature provides " +
                            concatType.parameterCount());
        }
        return new J8StringFactory(chars, constants, aCount);
    }

    public static void onSpinWait() {
    }
}
