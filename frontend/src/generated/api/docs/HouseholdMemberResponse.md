# HouseholdMemberResponse

## Properties

| Name     | Type    |
| -------- | ------- |
| `id`     | number  |
| `name`   | string  |
| `active` | boolean |
| `system` | boolean |

## Example

```typescript
import type { HouseholdMemberResponse } from '';

// TODO: Update the object below with actual values
const example = {
  id: null,
  name: null,
  active: null,
  system: null,
} satisfies HouseholdMemberResponse;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as HouseholdMemberResponse;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
