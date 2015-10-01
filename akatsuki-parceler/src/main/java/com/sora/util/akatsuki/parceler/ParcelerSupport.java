package com.sora.util.akatsuki.parceler;

import com.sora.util.akatsuki.TransformationTemplate;
import com.sora.util.akatsuki.TransformationTemplate.StatementTemplate;
import com.sora.util.akatsuki.TransformationTemplate.StatementTemplate.Type;
import com.sora.util.akatsuki.TypeConstraint;
import com.sora.util.akatsuki.TypeConstraint.Bound;
import com.sora.util.akatsuki.TypeFilter;

import org.parceler.Parcel;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Add {@code @IncludeClasses(ParcelerSupport.class) } to any class to enable
 */
//@formatter:off
@SuppressWarnings("unused")
@TransformationTemplate(
		save = @StatementTemplate("{{bundle}}.putParcelable({{keyName}} == null?null:{{keyName}}, org.parceler.Parcels.wrap(" +
				                          "com.sora.util.akatsuki.parceler.ParcelerSupport.resolveInterfaceClass(" +
				                          "{{fieldName}}), {{fieldName}}))"),
		restore = @StatementTemplate(type = Type.ASSIGNMENT,
				                     value = "org.parceler.Parcels.unwrap({{bundle}}.getParcelable({{keyName}}))",
				                     variable = "{{fieldName}}"),
		filters = {@TypeFilter(type = @TypeConstraint(type = Parcel.class)),
				   @TypeFilter(type = @TypeConstraint(type = List.class, bound = Bound.EXTENDS),
						       parameters = @TypeConstraint(type = Parcel.class)),
				   @TypeFilter(type = @TypeConstraint(type = Set.class, bound = Bound.EXTENDS),
				               parameters = @TypeConstraint(type = Parcel.class)),
				   @TypeFilter(type = @TypeConstraint(type = Map.class, bound = Bound.EXTENDS),
				               parameters = @TypeConstraint(type = Parcel.class))
		          }
		)
//@formatter:on
public class ParcelerSupport {

	public static Class<?> resolveInterfaceClass(Object input) {
		if (input instanceof List) {
			return List.class;
		} else if (input instanceof Set) {
			return Set.class;
		} else if (input instanceof Map) {
			return Map.class;
		}
		return input.getClass();
	}

}
