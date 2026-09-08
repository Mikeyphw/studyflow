"use strict";

export const TV_REVIEW_RATINGS=["again","hard","good","easy"] as const;
export type TvReviewRating=typeof TV_REVIEW_RATINGS[number];
export const TV_ERROR_CATEGORIES=["content-gap","forgotten","concept-confusion","interpretation","distraction","calculation","time-pressure","disputed","other"] as const;
export function tvReviewRatingOptionId(rating:TvReviewRating):string{return `rating-${rating}`;}
export function tvErrorCategoryOptionId(category:string):string{return `category-${category}`;}
export function tvReviewProgressPercent(completed:number,total:number):number{return total<=0?100:Math.max(0,Math.min(100,Math.round((Math.max(0,completed)/total)*100)));}
export function tvReviewIndexAfterCompletion(index:number,nextTotal:number):number{return nextTotal<=0?0:Math.max(0,Math.min(index,nextTotal-1));}
