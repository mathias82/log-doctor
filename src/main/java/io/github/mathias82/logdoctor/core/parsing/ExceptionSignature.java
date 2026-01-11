package io.github.mathias82.logdoctor.core.parsing;

import java.util.List;

public record ExceptionSignature(
        String topException,       // e.g. org.hibernate.tool.schema.spi.SchemaManagementException
        String message,            // e.g. Schema-validation: missing table [users.contact]
        List<String> stackFrames    // first N frames
) { }
