package net.unit8.bouncr.web.form;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * @author kawasima
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PermissionForm extends FormBase {
    private Long id;

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Size(max = 255)
    private String description;
}
