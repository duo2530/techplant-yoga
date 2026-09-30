import request from '@/utils/request'

export function listBooking(query) {
  return request({ url: '/admin/bookings', method: 'get', params: query })
}

export function getBooking(bookingId) {
  return request({ url: '/admin/bookings/' + bookingId, method: 'get' })
}
