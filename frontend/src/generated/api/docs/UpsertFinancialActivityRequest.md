# UpsertFinancialActivityRequest

## Properties

| Name           | Type    |
| -------------- | ------- |
| `name`         | string  |
| `activityType` | string  |
| `taxTreatment` | string  |
| `ownerId`      | number  |
| `propertyId`   | number  |
| `active`       | boolean |

## Example

```typescript
import type { UpsertFinancialActivityRequest } from '';

// TODO: Update the object below with actual values
const example = {
  name: null,
  activityType: null,
  taxTreatment: null,
  ownerId: null,
  propertyId: null,
  active: null,
} satisfies UpsertFinancialActivityRequest;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as UpsertFinancialActivityRequest;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
