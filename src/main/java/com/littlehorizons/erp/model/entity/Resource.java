package com.littlehorizons.erp.model.entity;

import com.littlehorizons.erp.exception.ValidationException;
import com.littlehorizons.erp.model.enums.ResourceCategory;
import com.littlehorizons.erp.model.enums.ResourceStatus;

import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One lendable item of the school inventory (a book, a microscope, a tablet...). It has a unique
 * inventory code, a name, a category, a physical location and a state. Every setter validates
 * BEFORE changing the value, so an invalid Resource cannot exist in memory.
 *
 * Which state changes are allowed is NOT decided here: ResourceStatus.manualTargets() holds the
 * rule and ResourceController applies it, so this class only stores the state.
 */
public class Resource {

    public static final int NAME_MAX = 150;
    public static final int LOCATION_MAX = 100;
    public static final int DESCRIPTION_MAX = 500;
    public static final int MIN_YEAR = 1900;

    /** 3 to 20 characters: upper case letters and digits separated by single hyphens (ex. LIB-001). */
    private static final Pattern CODE = Pattern.compile("(?=.{3,20}$)[A-Z0-9]+(?:-[A-Z0-9]+)*");
    /** Unlike the names of clubs or concepts, a resource may carry numbers (a model, a volume): "Tablet Galaxy Tab A9". */
    private static final Pattern TEXT = Pattern.compile("\\p{L}[\\p{L}\\p{N} '.,/:\\-]*");
    private static final String TEXT_MESSAGE = "empiece con una letra y use solo letras, números, espacios, "
            + "punto, coma, dos puntos, guion, barra o apóstrofe.";

    private Integer id;
    private String code;
    private String name;
    private ResourceCategory category;
    private String description;
    private LocalDate acquisitionDate;
    private String location;
    private ResourceStatus status = ResourceStatus.AVAILABLE;

    public Resource(String code, String name, ResourceCategory category, String description,
                    LocalDate acquisitionDate, String location) {
        setCode(code);
        setName(name);
        setCategory(category);
        setDescription(description);
        setAcquisitionDate(acquisitionDate);
        setLocation(location);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = checkCode(code);
    }

    /** Public so the form can validate the answer while it is typed. Returns the code in upper case. */
    public static String checkCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ValidationException("El código del recurso es obligatorio.");
        }
        String clean = code.trim().toUpperCase();
        if (!CODE.matcher(clean).matches()) {
            throw new ValidationException("El código debe tener de 3 a 20 caracteres: letras y números separados "
                    + "por guiones (ejemplo: LIB-001).");
        }
        return clean;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = checkName(name);
    }

    public static String checkName(String name) {
        return requireText(name, NAME_MAX, "El nombre del recurso", "El nombre del recurso es obligatorio.");
    }

    public ResourceCategory getCategory() {
        return category;
    }

    public void setCategory(ResourceCategory category) {
        if (category == null) {
            throw new ValidationException("La categoría del recurso es obligatoria.");
        }
        this.category = category;
    }

    /** Optional (null when blank). */
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = checkDescription(description);
    }

    public static String checkDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String clean = description.trim();
        if (clean.length() > DESCRIPTION_MAX) {
            throw new ValidationException("La descripción admite máximo " + DESCRIPTION_MAX + " caracteres.");
        }
        return clean;
    }

    /** Optional (null when unknown, for example a donated book). */
    public LocalDate getAcquisitionDate() {
        return acquisitionDate;
    }

    public void setAcquisitionDate(LocalDate acquisitionDate) {
        this.acquisitionDate = checkAcquisitionDate(acquisitionDate);
    }

    public static LocalDate checkAcquisitionDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        if (date.isAfter(LocalDate.now())) {
            throw new ValidationException("La fecha de adquisición no puede ser futura.");
        }
        if (date.getYear() < MIN_YEAR) {
            throw new ValidationException("La fecha de adquisición no puede ser anterior a " + MIN_YEAR + ".");
        }
        return date;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = checkLocation(location);
    }

    public static String checkLocation(String location) {
        return requireText(location, LOCATION_MAX, "La ubicación física", "La ubicación física es obligatoria.");
    }

    public ResourceStatus getStatus() {
        return status;
    }

    /** Plain setter: the allowed changes are checked by the controller (single guard), not here. */
    public void setStatus(ResourceStatus status) {
        if (status == null) {
            throw new ValidationException("El estado del recurso es obligatorio.");
        }
        this.status = status;
    }

    private static String requireText(String value, int maxLength, String fieldName, String missingMessage) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(missingMessage);
        }
        String clean = value.trim();
        if (clean.length() > maxLength) {
            throw new ValidationException(fieldName + ": máximo " + maxLength + " caracteres.");
        }
        if (!TEXT.matcher(clean).matches()) {
            throw new ValidationException(fieldName + ": " + TEXT_MESSAGE);
        }
        return clean;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Resource resource && id != null && Objects.equals(id, resource.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return code + " - " + name;
    }
}
