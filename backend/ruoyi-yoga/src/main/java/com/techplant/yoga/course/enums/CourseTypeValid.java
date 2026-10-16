package com.techplant.yoga.course.enums;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;

/** 课种取值校验注解。 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CourseTypeValid.Validator.class)
public @interface CourseTypeValid {
    /** 校验失败提示语，与 {@link CourseTypeEnum#INVALID_MESSAGE} 保持一致 */
    String message() default "课程类型取值为 1~4";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /**
     * 校验器：只把判断转给枚举，不复制取值范围
     */
    class Validator implements ConstraintValidator<CourseTypeValid, Integer> {
        @Override
        public boolean isValid(Integer value, ConstraintValidatorContext context) {
            // null 视为「未传」：必填由 @NotNull 负责，这里不重复报错
            return CourseTypeEnum.isValid(value);
        }
    }
}
