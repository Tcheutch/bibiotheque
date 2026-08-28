export enum ReservationStatus {
    EN_ATTENTE = 'EN_ATTENTE',
    DISPONIBLE = 'DISPONIBLE',
    ANNULEE = 'ANNULEE',
    EXPIREE = 'EXPIREE',
    HONOREE = 'HONOREE',
}

export class ReservationRequest {
    livreId: number;
    adherentId: number;
}

export class Reservation {
    id: number;
    livreId: number;
    adherentId: number;
    dateReservation: Date;
    dateExpiration: Date;
    statut: ReservationStatus;
}

// Vue résolue pour l'affichage : la réservation telle que renvoyée par l'API,
// enrichie des libellés livre/adhérent résolus côté frontend (décision Q1 :
// aucun champ supplémentaire sur le DTO backend).
export interface ReservationAffichage extends Reservation {
    titreLivre: string;
    nomAdherent: string;
}
