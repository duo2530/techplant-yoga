import request from '@/utils/request'

export function listReservation(query) {
  return request({ url: '/admin/reservations', method: 'get', params: query })
}

export function getReservation(reservationId) {
  return request({ url: '/admin/reservations/' + reservationId, method: 'get' })
}

export function cancelReservation(reservationId, data) {
  return request({ url: '/admin/reservations/' + reservationId + '/cancel', method: 'put', data })
}

export function checkInReservation(reservationId) {
  return request({ url: '/admin/reservations/' + reservationId + '/check-in', method: 'put' })
}
