/**
 * Client-side validation, deliberately mirroring the backend's Bean Validation constraints.
 *
 * This is duplication, and it is the right kind: client validation exists to give instant feedback
 * without a round trip, and server validation exists because the client can be bypassed entirely.
 * Neither substitutes for the other. The rules are kept in this one file so that when a backend
 * constraint changes there is a single place on this side to match it.
 *
 * Every function returns an error string, or `null` when the value is acceptable.
 */

/** Matches @Size(min = 2, max = 100) and @NotBlank on RegisterRequest.name */
export function validateName(name) {
  const value = (name ?? '').trim();
  if (!value) return 'Name is required';
  if (value.length < 2) return 'Name must be at least 2 characters';
  if (value.length > 100) return 'Name must not exceed 100 characters';
  return null;
}

/**
 * Matches @Email and @Size(max = 150).
 *
 * The pattern is intentionally permissive — "something@something.tld" — rather than an attempt at
 * RFC 5322. A stricter regex reliably rejects valid addresses (plus-addressing, new TLDs,
 * internationalised domains) and the only authoritative test of an address is whether mail to it
 * arrives.
 */
export function validateEmail(email) {
  const value = (email ?? '').trim();
  if (!value) return 'Email is required';
  if (value.length > 150) return 'Email must not exceed 150 characters';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value)) return 'Enter a valid email address';
  return null;
}

/** Matches @Size(min = 8, max = 72) plus the letter and digit patterns. */
export function validatePassword(password) {
  const value = password ?? '';
  if (!value) return 'Password is required';
  if (value.length < 8) return 'Password must be at least 8 characters';
  // 72 bytes is BCrypt's hard input limit; anything beyond it is silently truncated, so a longer
  // password would give a false sense of added strength.
  if (value.length > 72) return 'Password must not exceed 72 characters';
  if (!/[A-Za-z]/.test(value)) return 'Password must contain at least one letter';
  if (!/\d/.test(value)) return 'Password must contain at least one digit';
  return null;
}

export function validatePasswordConfirmation(password, confirmation) {
  if (!confirmation) return 'Please confirm your password';
  if (password !== confirmation) return 'Passwords do not match';
  return null;
}

const VALID_ROLES = ['Farmer', 'Land Buyer', 'Agriculture Expert', 'Admin'];

export function validateRole(role) {
  if (!role) return 'Please select a role';
  if (!VALID_ROLES.includes(role)) return 'Select one of the available roles';
  return null;
}

/** Matches @Size(min = 5, max = 200) on CreatePostRequest.title */
export function validatePostTitle(title) {
  const value = (title ?? '').trim();
  if (!value) return 'Title is required';
  if (value.length < 5) return 'Title must be at least 5 characters';
  if (value.length > 200) return 'Title must not exceed 200 characters';
  return null;
}

/** Matches @Size(min = 10, max = 5000) on CreatePostRequest.content */
export function validatePostContent(content) {
  const value = (content ?? '').trim();
  if (!value) return 'Please describe your question';
  if (value.length < 10) return 'Please give at least 10 characters of detail';
  if (value.length > 5000) return 'Content must not exceed 5000 characters';
  return null;
}

/** Matches @Size(min = 2, max = 3000) on CreateCommentRequest.content */
export function validateComment(content) {
  const value = (content ?? '').trim();
  if (!value) return 'Reply cannot be empty';
  if (value.length > 3000) return 'Reply must not exceed 3000 characters';
  return null;
}

/** Matches the numeric constraints on LandRequest. */
export function validateLandListing(form) {
  const errors = {};

  const title = (form.title ?? '').trim();
  if (!title) errors.title = 'Title is required';
  else if (title.length < 5) errors.title = 'Title must be at least 5 characters';
  else if (title.length > 150) errors.title = 'Title must not exceed 150 characters';

  if (!(form.location ?? '').trim()) errors.location = 'Location is required';
  if (!(form.district ?? '').trim()) errors.district = 'District is required';
  if (!(form.soilType ?? '').trim()) errors.soilType = 'Select a soil type';

  const price = Number(form.price);
  if (form.price === '' || form.price == null) errors.price = 'Price is required';
  else if (Number.isNaN(price)) errors.price = 'Price must be a number';
  else if (price < 1000) errors.price = 'Price must be at least ₹1,000';

  const size = Number(form.sizeInAcres);
  if (form.sizeInAcres === '' || form.sizeInAcres == null) errors.sizeInAcres = 'Size is required';
  else if (Number.isNaN(size) || size <= 0) errors.sizeInAcres = 'Size must be greater than zero';
  else if (size > 100000) errors.sizeInAcres = 'Size must not exceed 100,000 acres';

  const depth = Number(form.groundwaterLevelDepth);
  if (form.groundwaterLevelDepth === '' || form.groundwaterLevelDepth == null) {
    errors.groundwaterLevelDepth = 'Groundwater depth is required';
  } else if (Number.isNaN(depth) || depth < 0) {
    errors.groundwaterLevelDepth = 'Depth cannot be negative';
  } else if (depth > 1000) {
    errors.groundwaterLevelDepth = 'Depth beyond 1000m is not plausible';
  }

  if (form.annualRainfallMm !== '' && form.annualRainfallMm != null) {
    const rainfall = Number(form.annualRainfallMm);
    if (Number.isNaN(rainfall) || rainfall < 0) errors.annualRainfallMm = 'Rainfall cannot be negative';
    else if (rainfall > 12000) errors.annualRainfallMm = 'Rainfall beyond 12000mm is not plausible';
  }

  // India's bounding box, same as the backend's @DecimalMin/@DecimalMax. Catches transposed
  // latitude/longitude pairs, which would otherwise drop a map pin in the Indian Ocean.
  if (form.latitude !== '' && form.latitude != null) {
    const latitude = Number(form.latitude);
    if (Number.isNaN(latitude) || latitude < 6 || latitude > 38) {
      errors.latitude = 'Latitude must be within India (6 to 38)';
    }
  }
  if (form.longitude !== '' && form.longitude != null) {
    const longitude = Number(form.longitude);
    if (Number.isNaN(longitude) || longitude < 68 || longitude > 98) {
      errors.longitude = 'Longitude must be within India (68 to 98)';
    }
  }

  return errors;
}

/** Convenience: validates a whole registration form in one call. */
export function validateRegistration({ name, email, password, confirmPassword, role }) {
  const errors = {};
  const nameError = validateName(name);
  const emailError = validateEmail(email);
  const passwordError = validatePassword(password);
  const roleError = validateRole(role);

  if (nameError) errors.name = nameError;
  if (emailError) errors.email = emailError;
  if (passwordError) errors.password = passwordError;
  if (roleError) errors.role = roleError;

  if (confirmPassword !== undefined) {
    const confirmError = validatePasswordConfirmation(password, confirmPassword);
    if (confirmError) errors.confirmPassword = confirmError;
  }

  return errors;
}

export function validateLogin({ email, password }) {
  const errors = {};
  const emailError = validateEmail(email);
  if (emailError) errors.email = emailError;
  if (!password) errors.password = 'Password is required';
  return errors;
}

/** True when a validation-errors object has no entries. */
export function isValid(errors) {
  return Object.keys(errors).length === 0;
}
