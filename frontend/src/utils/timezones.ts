export interface TimezoneOption {
  value: string
  label: string
}

export const TIMEZONES: TimezoneOption[] = [
  { value: 'UTC', label: 'UTC' },

  // Europe
  { value: 'Europe/London', label: 'London (UTC+0/+1)' },
  { value: 'Europe/Dublin', label: 'Dublin (UTC+0/+1)' },
  { value: 'Europe/Lisbon', label: 'Lisbon (UTC+0/+1)' },
  { value: 'Europe/Paris', label: 'Paris (UTC+1/+2)' },
  { value: 'Europe/Berlin', label: 'Berlin (UTC+1/+2)' },
  { value: 'Europe/Amsterdam', label: 'Amsterdam (UTC+1/+2)' },
  { value: 'Europe/Rome', label: 'Rome (UTC+1/+2)' },
  { value: 'Europe/Madrid', label: 'Madrid (UTC+1/+2)' },
  { value: 'Europe/Warsaw', label: 'Warsaw (UTC+1/+2)' },
  { value: 'Europe/Stockholm', label: 'Stockholm (UTC+1/+2)' },
  { value: 'Europe/Helsinki', label: 'Helsinki (UTC+2/+3)' },
  { value: 'Europe/Kyiv', label: 'Kyiv (UTC+2/+3)' },
  { value: 'Europe/Bucharest', label: 'Bucharest (UTC+2/+3)' },
  { value: 'Europe/Athens', label: 'Athens (UTC+2/+3)' },
  { value: 'Europe/Moscow', label: 'Moscow (UTC+3)' },
  { value: 'Europe/Istanbul', label: 'Istanbul (UTC+3)' },

  // Americas
  { value: 'America/New_York', label: 'New York (UTC-5/-4)' },
  { value: 'America/Toronto', label: 'Toronto (UTC-5/-4)' },
  { value: 'America/Chicago', label: 'Chicago (UTC-6/-5)' },
  { value: 'America/Denver', label: 'Denver (UTC-7/-6)' },
  { value: 'America/Phoenix', label: 'Phoenix (UTC-7)' },
  { value: 'America/Los_Angeles', label: 'Los Angeles (UTC-8/-7)' },
  { value: 'America/Vancouver', label: 'Vancouver (UTC-8/-7)' },
  { value: 'America/Anchorage', label: 'Anchorage (UTC-9/-8)' },
  { value: 'America/Sao_Paulo', label: 'São Paulo (UTC-3/-2)' },
  { value: 'America/Argentina/Buenos_Aires', label: 'Buenos Aires (UTC-3)' },
  { value: 'America/Bogota', label: 'Bogotá (UTC-5)' },
  { value: 'America/Mexico_City', label: 'Mexico City (UTC-6/-5)' },

  // Asia
  { value: 'Asia/Dubai', label: 'Dubai (UTC+4)' },
  { value: 'Asia/Kolkata', label: 'India (UTC+5:30)' },
  { value: 'Asia/Dhaka', label: 'Dhaka (UTC+6)' },
  { value: 'Asia/Bangkok', label: 'Bangkok (UTC+7)' },
  { value: 'Asia/Singapore', label: 'Singapore (UTC+8)' },
  { value: 'Asia/Shanghai', label: 'Shanghai (UTC+8)' },
  { value: 'Asia/Hong_Kong', label: 'Hong Kong (UTC+8)' },
  { value: 'Asia/Tokyo', label: 'Tokyo (UTC+9)' },
  { value: 'Asia/Seoul', label: 'Seoul (UTC+9)' },

  // Pacific & Africa
  { value: 'Australia/Sydney', label: 'Sydney (UTC+10/+11)' },
  { value: 'Pacific/Auckland', label: 'Auckland (UTC+12/+13)' },
  { value: 'Africa/Cairo', label: 'Cairo (UTC+2/+3)' },
  { value: 'Africa/Johannesburg', label: 'Johannesburg (UTC+2)' },
  { value: 'Africa/Lagos', label: 'Lagos (UTC+1)' },
]

export function getBrowserTimezone(): string {
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone
  return TIMEZONES.find((t) => t.value === tz)?.value ?? 'UTC'
}
