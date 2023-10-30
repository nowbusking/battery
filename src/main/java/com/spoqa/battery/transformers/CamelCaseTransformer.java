/**
 * Copyright (c) 2014-2015 Spoqa, All Rights Reserved.
 */

package com.spoqa.battery.transformers;

import com.spoqa.battery.FieldNameTransformer;
import com.spoqa.battery.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class CamelCaseTransformer implements FieldNameTransformer {

    @Override
    public List<String> decode(String input) {
        return StringUtils.splitByCase(input);
    }

    @Override
    public String encode(List<String> parts) {
        if (parts.isEmpty())
            return "";

        List<String> output = new ArrayList<>();

        for (int i = 0; i < parts.size(); ++i) {
            if (i == 0)
                output.add(parts.get(i));
            else
                output.add(StringUtils.uppercaseFirst(parts.get(i)));
        }

        return StringUtils.join(output, "");
    }
}
