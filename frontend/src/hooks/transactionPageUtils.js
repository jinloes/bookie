// @ts-check

/**
 * @param {Array<{ date?: string | null }>} records
 */
export function buildYearOptions(records) {
  const years = [...new Set(records.map((record) => record.date?.slice(0, 4)).filter(Boolean))]
    .sort()
    .reverse();

  return years.map((year) => ({ value: year, label: year }));
}

/**
 * @template {{ id?: number, name?: string, address?: string | null }} T
 * @param {T[]} properties
 * @param {string | null | undefined} propertyName
 * @returns {T | null}
 */
export function findMatchingProperty(properties, propertyName) {
  const suggestedProperty = propertyName?.trim().toLowerCase();

  if (!suggestedProperty) {
    return null;
  }

  return (
    properties.find((property) => property.name?.toLowerCase() === suggestedProperty) ??
    properties.find(
      (property) =>
        property.address?.toLowerCase().includes(suggestedProperty) ||
        suggestedProperty.includes(property.address?.toLowerCase() ?? '')
    ) ??
    null
  );
}

/**
 * Ledger rows carry neutral categories, so compatible financial categories are deduplicated by
 * the neutral category ID each maps to.
 *
 * @param {Array<{ neutralCategoryId?: number | null, label?: string | null }>} compatibleCategories
 */
export function buildNeutralCategoryOptions(compatibleCategories) {
  const options = new Map();
  compatibleCategories
    .filter((category) => category.neutralCategoryId)
    .forEach((category) => {
      const value = String(category.neutralCategoryId);
      if (!options.has(value)) {
        options.set(value, category.label ?? value);
      }
    });
  return [...options].map(([value, label]) => ({ value, label }));
}

/**
 * @param {Array<{ id?: number | null, name?: string | null, active?: boolean }>} activities
 */
export function buildActiveActivityOptions(activities) {
  return activities
    .filter((activity) => activity.active && activity.id != null)
    .map((activity) => ({ value: String(activity.id), label: activity.name ?? '' }));
}

/**
 * @param {Array<{ owner?: { id?: number | null, name?: string | null } | null }>} activities
 */
export function buildOwnerOptions(activities) {
  const owners = new Map();
  activities.forEach((activity) => {
    if (activity.owner?.id) {
      owners.set(String(activity.owner.id), activity.owner.name ?? '');
    }
  });
  return [...owners].map(([value, label]) => ({ value, label }));
}

/**
 * Validates common ledger transaction form values without a runtime schema library.
 *
 * @param {{
 *   amount?: string | number | null,
 *   description?: string | null,
 *   date?: string | null,
 *   source?: string | null,
 *   activityId?: string | number | null,
 *   categoryId?: string | number | null,
 *   payerId?: string | number | null,
 * }} values
 */
export function validateTransactionForm(values) {
  /** @type {Record<string, string>} */
  const errors = {};

  const rawAmount = String(values.amount ?? '').trim();
  const amount = rawAmount === '' ? Number.NaN : Number.parseFloat(rawAmount);
  if (Number.isNaN(amount) || amount < 0) {
    errors.amount = 'Amount must be a positive number';
  }

  const description = String(values.description ?? '');
  if (description.length < 1) {
    errors.description = 'Description is required';
  } else if (description.length > 255) {
    errors.description = 'Description must be at most 255 characters';
  }

  const date = String(values.date ?? '');
  if (date.length < 1) {
    errors.date = 'Date is required';
  }

  if (values.source != null && String(values.source).length > 255) {
    errors.source = 'Source must be at most 255 characters';
  }

  const activityId = values.activityId ? Number(values.activityId) : 0;
  if (!Number.isFinite(activityId) || activityId <= 0) {
    errors.activityId = 'Activity is required';
  }

  const categoryId = values.categoryId ? Number(values.categoryId) : 0;
  if (!Number.isFinite(categoryId) || categoryId <= 0) {
    errors.categoryId = 'Category is required';
  }

  const payerId = values.payerId ? Number(values.payerId) : null;
  if (payerId !== null && (!Number.isFinite(payerId) || payerId <= 0)) {
    errors.payerId = 'Invalid payer';
  }

  return {
    valid: Object.keys(errors).length === 0,
    errors,
    parsed: {
      amount,
      description,
      date,
      source: values.source ? String(values.source) : undefined,
      activityId,
      categoryId,
      payerId: payerId ?? undefined,
    },
  };
}
