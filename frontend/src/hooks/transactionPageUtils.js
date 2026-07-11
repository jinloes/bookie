export function buildYearOptions(records) {
  const years = [...new Set(records.map((record) => record.date?.slice(0, 4)).filter(Boolean))]
    .sort()
    .reverse();

  return years.map((year) => ({ value: year, label: year }));
}

export function findMatchingProperty(properties, propertyName) {
  const suggestedProperty = propertyName?.trim().toLowerCase();

  if (!suggestedProperty) {
    return null;
  }

  return (
    properties.find((property) => property.name.toLowerCase() === suggestedProperty) ??
    properties.find(
      (property) =>
        property.address?.toLowerCase().includes(suggestedProperty) ||
        suggestedProperty.includes(property.address?.toLowerCase() ?? '')
    ) ??
    null
  );
}
