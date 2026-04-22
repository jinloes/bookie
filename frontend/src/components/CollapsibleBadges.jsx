import React, { useState } from 'react'
import { Stack, Anchor, Collapse, Group, Badge, Text } from '@mantine/core'

/**
 * Renders a list of badges behind a show/hide toggle.
 * @param {Array}    items    - array of items to display
 * @param {string}   color    - Mantine color for the badges
 * @param {string}   [variant='outline'] - badge variant
 * @param {function} getKey   - (item) => unique key string
 * @param {function} getLabel - (item) => display text
 * @param {function} [getTitle] - (item) => tooltip title string
 */
export default function CollapsibleBadges({ items, color, variant = 'outline', getKey, getLabel, getTitle }) {
  const [open, setOpen] = useState(false)
  if (!items?.length) return <Text c="dimmed" size="sm">—</Text>
  return (
    <Stack gap={4}>
      <Anchor size="sm" onClick={() => setOpen(o => !o)}>
        {open ? 'Hide' : `Show ${items.length}`}
      </Anchor>
      <Collapse in={open}>
        <Group gap={4} wrap="wrap">
          {items.map(item => (
            <Badge
              key={getKey(item)}
              variant={variant}
              color={color}
              size="sm"
              title={getTitle?.(item)}
            >
              {getLabel(item)}
            </Badge>
          ))}
        </Group>
      </Collapse>
    </Stack>
  )
}