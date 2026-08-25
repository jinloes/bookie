# FinancialActivityResponse

## Properties

| Name                  | Type                                                        |
| --------------------- | ----------------------------------------------------------- |
| `id`                  | number                                                      |
| `name`                | string                                                      |
| `activityType`        | string                                                      |
| `taxTreatment`        | string                                                      |
| `owner`               | [HouseholdMemberRefResponse](HouseholdMemberRefResponse.md) |
| `property`            | [PropertyRefResponse](PropertyRefResponse.md)               |
| `active`              | boolean                                                     |
| `needsClassification` | boolean                                                     |

## Example

```typescript
import type { FinancialActivityResponse } from '';

// TODO: Update the object below with actual values
const example = {
  id: null,
  name: null,
  activityType: null,
  taxTreatment: null,
  owner: null,
  property: null,
  active: null,
  needsClassification: null,
} satisfies FinancialActivityResponse;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FinancialActivityResponse;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
