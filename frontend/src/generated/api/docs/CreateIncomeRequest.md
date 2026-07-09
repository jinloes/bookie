
# CreateIncomeRequest


## Properties

Name | Type
------------ | -------------
`amount` | number
`description` | string
`date` | Date
`source` | string
`propertyId` | number
`payerId` | number
`sourceType` | string
`receiptOneDriveId` | string
`receiptFileName` | string

## Example

```typescript
import type { CreateIncomeRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "amount": null,
  "description": null,
  "date": null,
  "source": null,
  "propertyId": null,
  "payerId": null,
  "sourceType": null,
  "receiptOneDriveId": null,
  "receiptFileName": null,
} satisfies CreateIncomeRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CreateIncomeRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


