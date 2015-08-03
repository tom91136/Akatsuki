package com.sora.util.akatsuki.parceler;

import com.sora.util.akatsuki.TransformationTemplate;
import com.sora.util.akatsuki.TransformationTemplate.Execution;
import com.sora.util.akatsuki.TypeConstraint;

import org.parceler.Parcel;

/**
 * Add {@code @IncludeClasses(ParcelerSupport.class) } to any class to enable
 */
//@formatter:off
@TransformationTemplate(
		save = "{{bundle}}.putParcelable(\"{{keyName}}\", org.parceler.Parcels.wrap({{fieldName}}))",
		restore = "{{fieldName}} = org.parceler.Parcels.unwrap({{bundle}}.getParcelable(\"{{keyName}}\"))",
		constraints = @TypeConstraint(types = {Parcel.class}),
		execution = Execution.BEFORE)
//@formatter:on
public class ParcelerSupport {

}
